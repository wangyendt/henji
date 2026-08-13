from __future__ import annotations

import json
import os
import secrets
from contextlib import contextmanager
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path
from typing import Any, Iterator, Literal
from uuid import UUID

import psycopg
from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, status
from pydantic import BaseModel, ConfigDict, Field, field_validator
from psycopg.rows import dict_row
from starlette.middleware.base import BaseHTTPMiddleware


def read_secret(env_name: str) -> str:
    path = os.environ.get(env_name, "")
    if not path:
        raise RuntimeError(f"{env_name} is required")
    value = Path(path).read_text(encoding="utf-8").strip()
    if not value:
        raise RuntimeError(f"{env_name} is empty")
    return value


API_TOKEN = read_secret("API_TOKEN_FILE")
DB_PASSWORD = read_secret("DB_PASSWORD_FILE")
DATABASE_PARAMETERS = {
    "host": os.environ.get("DB_HOST", "personal-postgres"),
    "port": int(os.environ.get("DB_PORT", "5432")),
    "dbname": os.environ.get("DB_NAME", "personal_knowledge"),
    "user": os.environ.get("DB_USER", "henji_sync"),
    "password": DB_PASSWORD,
    "connect_timeout": 5,
    "application_name": "henji-sync-api",
}


@contextmanager
def connection() -> Iterator[psycopg.Connection]:
    with psycopg.connect(**DATABASE_PARAMETERS, row_factory=dict_row) as conn:
        yield conn


class BodyLimitMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        content_length = request.headers.get("content-length")
        if content_length and int(content_length) > 2 * 1024 * 1024:
            raise HTTPException(status_code=413, detail="request body is too large")
        return await call_next(request)


class SyncEventIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    eventId: UUID
    entityType: str = Field(min_length=1, max_length=64, pattern=r"^[a-z][a-z0-9_]*$")
    entityId: str = Field(min_length=1, max_length=255)
    dedupeKey: str | None = Field(default=None, min_length=1, max_length=255)
    operation: Literal["upsert", "delete"]
    schemaVersion: int = Field(default=1, ge=1, le=1000)
    occurredAt: int = Field(ge=0)
    payload: dict[str, Any] = Field(default_factory=dict)

    @field_validator("payload")
    @classmethod
    def payload_must_be_small(cls, value: dict[str, Any]) -> dict[str, Any]:
        if len(json.dumps(value, ensure_ascii=False).encode("utf-8")) > 512 * 1024:
            raise ValueError("one event payload cannot exceed 512 KiB")
        return value


class PushRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    deviceId: UUID
    events: list[SyncEventIn] = Field(min_length=1, max_length=200)


class PullEvent(BaseModel):
    cursor: int
    eventId: UUID
    deviceId: UUID
    entityType: str
    entityId: str
    dedupeKey: str | None
    operation: Literal["upsert", "delete"]
    schemaVersion: int
    occurredAt: int
    payload: dict[str, Any]


class PushResponse(BaseModel):
    acknowledged: list[UUID]
    serverCursor: int


class PullResponse(BaseModel):
    events: list[PullEvent]
    nextCursor: int
    serverCursor: int
    hasMore: bool


def require_token(authorization: str | None = Header(default=None)) -> None:
    prefix = "Bearer "
    if authorization is None or not authorization.startswith(prefix):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="missing bearer token")
    supplied = authorization[len(prefix):]
    if not secrets.compare_digest(supplied, API_TOKEN):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid bearer token")


def epoch_millis(value: datetime) -> int:
    return int(value.timestamp() * 1000)


def server_cursor(cur: psycopg.Cursor) -> int:
    cur.execute("SELECT COALESCE(max(cursor), 0) AS cursor FROM health.sync_events")
    return int(cur.fetchone()["cursor"])


def effective_client_dedupe_key(event: SyncEventIn) -> str | None:
    if event.entityType != "weight_record" or event.operation != "upsert":
        return event.dedupeKey
    try:
        measured_at = int(event.payload["measuredAt"])
        centi_kg = int(
            (Decimal(str(event.payload["weightKg"])) * 100).quantize(
                Decimal("1"), rounding=ROUND_HALF_UP
            )
        )
    except (KeyError, TypeError, ValueError, InvalidOperation):
        raise HTTPException(status_code=422, detail="weight payload needs measuredAt and weightKg")
    return f"{measured_at // 60000}:{centi_kg}"


app = FastAPI(title="HengJi Personal Sync", version="1.0.0", docs_url=None, redoc_url=None)
app.add_middleware(BodyLimitMiddleware)


@app.get("/healthz")
def healthz() -> dict[str, Any]:
    with connection() as conn, conn.cursor() as cur:
        cur.execute("SELECT 1 AS ok")
        cur.fetchone()
    return {"ok": True, "service": "henji-sync"}


@app.post("/v1/sync/push", response_model=PushResponse, dependencies=[Depends(require_token)])
def push(request: PushRequest) -> PushResponse:
    acknowledged: list[UUID] = []
    with connection() as conn, conn.transaction(), conn.cursor() as cur:
        for event in request.events:
            requested_dedupe_key = effective_client_dedupe_key(event)
            # A logical key also serializes alternate source IDs for the same measurement.
            cur.execute(
                "SELECT pg_advisory_xact_lock(hashtextextended(%s, 0))",
                (f"{event.entityType}:{requested_dedupe_key or event.entityId}",),
            )
            cur.execute(
                "SELECT cursor FROM health.sync_events WHERE event_id = %s",
                (event.eventId,),
            )
            if cur.fetchone() is not None:
                acknowledged.append(event.eventId)
                continue

            cur.execute(
                """
                SELECT deleted, operation, schema_version, occurred_at, payload, dedupe_key
                FROM health.sync_objects
                WHERE entity_type = %s AND entity_id = %s
                FOR UPDATE
                """,
                (event.entityType, event.entityId),
            )
            current = cur.fetchone()

            tombstone = current if current is not None and current["deleted"] else None
            if tombstone is None and requested_dedupe_key is not None:
                cur.execute(
                    """
                    SELECT deleted, operation, schema_version, occurred_at, payload, dedupe_key
                    FROM health.sync_objects
                    WHERE entity_type = %s AND dedupe_key = %s AND deleted
                    ORDER BY updated_at DESC
                    LIMIT 1
                    FOR UPDATE
                    """,
                    (event.entityType, requested_dedupe_key),
                )
                tombstone = cur.fetchone()

            # A tombstone is permanent across both source IDs and devices. Recreating an item
            # must use a new ID and, for weight records, a genuinely different logical key.
            tombstoned = tombstone is not None
            effective_operation = "delete" if tombstoned else event.operation
            effective_payload = {} if effective_operation == "delete" else event.payload
            effective_schema_version = (
                tombstone["schema_version"] if tombstoned else event.schemaVersion
            )
            effective_occurred_at = (
                tombstone["occurred_at"]
                if tombstoned
                else datetime.fromtimestamp(event.occurredAt / 1000, tz=timezone.utc)
            )
            effective_dedupe_key = (
                tombstone["dedupe_key"]
                if tombstoned
                else requested_dedupe_key or (current["dedupe_key"] if current is not None else None)
            )

            cur.execute(
                """
                INSERT INTO health.sync_events (
                    event_id, device_id, entity_type, entity_id, dedupe_key, operation,
                    schema_version, occurred_at, payload,
                    submitted_operation, submitted_payload
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb, %s, %s::jsonb)
                RETURNING cursor
                """,
                (
                    event.eventId,
                    request.deviceId,
                    event.entityType,
                    event.entityId,
                    effective_dedupe_key,
                    effective_operation,
                    effective_schema_version,
                    effective_occurred_at,
                    json.dumps(effective_payload, ensure_ascii=False),
                    event.operation,
                    json.dumps(event.payload, ensure_ascii=False),
                ),
            )
            cursor = int(cur.fetchone()["cursor"])
            cur.execute(
                """
                INSERT INTO health.sync_objects (
                    entity_type, entity_id, dedupe_key, latest_cursor, latest_event_id, device_id,
                    operation, deleted, schema_version, occurred_at, payload
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb)
                ON CONFLICT (entity_type, entity_id) DO UPDATE SET
                    dedupe_key = EXCLUDED.dedupe_key,
                    latest_cursor = EXCLUDED.latest_cursor,
                    latest_event_id = EXCLUDED.latest_event_id,
                    device_id = EXCLUDED.device_id,
                    operation = EXCLUDED.operation,
                    deleted = EXCLUDED.deleted,
                    schema_version = EXCLUDED.schema_version,
                    occurred_at = EXCLUDED.occurred_at,
                    updated_at = now(),
                    payload = EXCLUDED.payload
                """,
                (
                    event.entityType,
                    event.entityId,
                    effective_dedupe_key,
                    cursor,
                    event.eventId,
                    request.deviceId,
                    effective_operation,
                    effective_operation == "delete",
                    effective_schema_version,
                    effective_occurred_at,
                    json.dumps(effective_payload, ensure_ascii=False),
                ),
            )
            acknowledged.append(event.eventId)
        latest = server_cursor(cur)
    return PushResponse(acknowledged=acknowledged, serverCursor=latest)


@app.get("/v1/sync/pull", response_model=PullResponse, dependencies=[Depends(require_token)])
def pull(
    after: int = Query(default=0, ge=0),
    limit: int = Query(default=200, ge=1, le=500),
) -> PullResponse:
    with connection() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT cursor, event_id, device_id, entity_type, entity_id, dedupe_key, operation,
                   schema_version, occurred_at, payload
            FROM health.sync_events
            WHERE cursor > %s
            ORDER BY cursor
            LIMIT %s
            """,
            (after, limit + 1),
        )
        rows = cur.fetchall()
        more = len(rows) > limit
        page = rows[:limit]
        latest = server_cursor(cur)

    events = [
        PullEvent(
            cursor=row["cursor"],
            eventId=row["event_id"],
            deviceId=row["device_id"],
            entityType=row["entity_type"],
            entityId=row["entity_id"],
            dedupeKey=row["dedupe_key"],
            operation=row["operation"],
            schemaVersion=row["schema_version"],
            occurredAt=epoch_millis(row["occurred_at"]),
            payload=row["payload"],
        )
        for row in page
    ]
    next_cursor = events[-1].cursor if events else min(after, latest)
    return PullResponse(
        events=events,
        nextCursor=next_cursor,
        serverCursor=latest,
        hasMore=more,
    )
