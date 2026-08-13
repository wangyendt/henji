BEGIN;

SET ROLE personal_knowledge_owner;

CREATE TABLE IF NOT EXISTS health.sync_events (
    cursor              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id            UUID NOT NULL UNIQUE,
    device_id           UUID NOT NULL,
    entity_type         TEXT NOT NULL CHECK (entity_type ~ '^[a-z][a-z0-9_]{0,63}$'),
    entity_id           TEXT NOT NULL CHECK (length(entity_id) BETWEEN 1 AND 255),
    dedupe_key          TEXT CHECK (dedupe_key IS NULL OR length(dedupe_key) BETWEEN 1 AND 255),
    operation           TEXT NOT NULL CHECK (operation IN ('upsert', 'delete')),
    schema_version      INTEGER NOT NULL CHECK (schema_version BETWEEN 1 AND 1000),
    occurred_at         TIMESTAMPTZ NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload             JSONB NOT NULL DEFAULT '{}'::jsonb,
    submitted_operation TEXT NOT NULL CHECK (submitted_operation IN ('upsert', 'delete')),
    submitted_payload   JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS health_sync_events_entity_idx
    ON health.sync_events (entity_type, entity_id, cursor DESC);
CREATE INDEX IF NOT EXISTS health_sync_events_received_idx
    ON health.sync_events (received_at DESC);
CREATE INDEX IF NOT EXISTS health_sync_events_payload_gin
    ON health.sync_events USING GIN (payload);

CREATE TABLE IF NOT EXISTS health.sync_objects (
    entity_type    TEXT NOT NULL CHECK (entity_type ~ '^[a-z][a-z0-9_]{0,63}$'),
    entity_id      TEXT NOT NULL CHECK (length(entity_id) BETWEEN 1 AND 255),
    dedupe_key     TEXT CHECK (dedupe_key IS NULL OR length(dedupe_key) BETWEEN 1 AND 255),
    latest_cursor  BIGINT NOT NULL REFERENCES health.sync_events(cursor),
    latest_event_id UUID NOT NULL REFERENCES health.sync_events(event_id),
    device_id      UUID NOT NULL,
    operation      TEXT NOT NULL CHECK (operation IN ('upsert', 'delete')),
    deleted        BOOLEAN NOT NULL DEFAULT false,
    schema_version INTEGER NOT NULL CHECK (schema_version BETWEEN 1 AND 1000),
    occurred_at    TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload        JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (entity_type, entity_id),
    CHECK ((deleted AND operation = 'delete') OR (NOT deleted AND operation = 'upsert'))
);

CREATE INDEX IF NOT EXISTS health_sync_objects_updated_idx
    ON health.sync_objects (updated_at DESC);
CREATE INDEX IF NOT EXISTS health_sync_objects_payload_gin
    ON health.sync_objects USING GIN (payload);
CREATE INDEX IF NOT EXISTS health_sync_objects_deleted_dedupe_idx
    ON health.sync_objects (entity_type, dedupe_key)
    WHERE deleted AND dedupe_key IS NOT NULL;

COMMENT ON TABLE health.sync_events IS
    'Append-only, idempotent device synchronization log. Payload excludes meal photos.';
COMMENT ON TABLE health.sync_objects IS
    'Latest effective health object state; delete rows are retained as tombstones.';
COMMENT ON COLUMN health.sync_events.submitted_payload IS
    'Original client payload retained for audit when a tombstone prevents resurrection.';

RESET ROLE;

GRANT USAGE ON SCHEMA health TO henji_sync;
GRANT SELECT, INSERT ON health.sync_events TO henji_sync;
GRANT SELECT, INSERT, UPDATE ON health.sync_objects TO henji_sync;
GRANT USAGE, SELECT ON SEQUENCE health.sync_events_cursor_seq TO henji_sync;

COMMIT;
