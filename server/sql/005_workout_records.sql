BEGIN;

SET ROLE personal_knowledge_owner;

DROP VIEW IF EXISTS health.daily_wellness_records;

WITH legacy_workouts AS (
    SELECT
        current.device_id,
        current.occurred_at,
        current.payload AS legacy_payload,
        workout.value AS workout_json,
        (extract(epoch FROM (workout.value->>'startAt')::timestamptz) * 1000)::bigint AS start_at_ms,
        'vivo-workout-'
            || ((extract(epoch FROM (workout->>'startAt')::timestamptz) * 1000)::bigint)::text
            || '-'
            || regexp_replace(workout.value->>'type', E'[\\s/]+', '-', 'g') AS workout_entity_id
    FROM health.sync_objects AS current
    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(current.payload->'workouts', '[]'::jsonb)) AS workout(value)
    WHERE current.entity_type = 'daily_wellness_record'
      AND NOT current.deleted
      AND workout.value ? 'type'
      AND workout.value ? 'startAt'
), prepared AS (
    SELECT
        *,
        'vivo_health_share:' || start_at_ms::text || ':' || (workout_json->>'type') AS workout_dedupe_key,
        jsonb_build_object(
            'updatedAt', COALESCE((legacy_payload->>'updatedAt')::bigint, start_at_ms),
            'source', 'vivo_health_share',
            'workoutType', workout_json->>'type',
            'workoutCategory', CASE
                WHEN workout_json->>'type' LIKE '%游泳%' THEN 'swimming'
                WHEN workout_json->>'type' LIKE '%跑%' THEN 'running'
                WHEN workout_json->>'type' LIKE '%步行%' OR workout_json->>'type' LIKE '%健走%' THEN 'walking'
                ELSE 'other'
            END,
            'startAt', start_at_ms,
            'durationSeconds', CASE
                WHEN workout_json ? 'durationMinutes' THEN round((workout_json->>'durationMinutes')::numeric * 60)::integer
                ELSE NULL
            END,
            'distanceMeters', CASE
                WHEN workout_json ? 'distanceKm' THEN (workout_json->>'distanceKm')::double precision * 1000
                ELSE NULL
            END,
            'caloriesKcal', workout_json->'caloriesKcal',
            'averageHeartRateBpm', workout_json->'averageHeartRateBpm',
            'maximumHeartRateBpm', workout_json->'maximumHeartRateBpm',
            'averagePaceSecondsPerKm', NULL,
            'averagePaceSecondsPer100Meters', NULL,
            'averageCadencePerMinute', NULL,
            'steps', NULL,
            'averageStrideCentimeters', NULL,
            'elevationGainMeters', NULL,
            'poolLengthMeters', NULL,
            'lengths', NULL,
            'strokes', NULL,
            'averageSwolf', NULL,
            'averageStrokeRatePerMinute', NULL,
            'mainStroke', NULL,
            'confidence', COALESCE((legacy_payload->>'confidence')::double precision, 0.5),
            'rawAnalysis', legacy_payload->'rawAnalysis'
        ) AS workout_payload
    FROM legacy_workouts
), inserted_events AS (
    INSERT INTO health.sync_events (
        event_id, device_id, entity_type, entity_id, dedupe_key, operation,
        schema_version, occurred_at, payload, submitted_operation, submitted_payload
    )
    SELECT
        md5(random()::text || clock_timestamp()::text)::uuid,
        prepared.device_id,
        'workout_record',
        prepared.workout_entity_id,
        prepared.workout_dedupe_key,
        'upsert',
        1,
        prepared.occurred_at,
        prepared.workout_payload,
        'upsert',
        prepared.workout_payload
    FROM prepared
    WHERE NOT EXISTS (
        SELECT 1
        FROM health.sync_objects AS existing
        WHERE existing.entity_type = 'workout_record'
          AND existing.entity_id = prepared.workout_entity_id
    )
    RETURNING *
)
INSERT INTO health.sync_objects (
    entity_type, entity_id, dedupe_key, latest_cursor, latest_event_id,
    device_id, operation, deleted, schema_version, occurred_at, updated_at, payload
)
SELECT
    entity_type, entity_id, dedupe_key, cursor, event_id,
    device_id, operation, false, schema_version, occurred_at, now(), payload
FROM inserted_events
ON CONFLICT (entity_type, entity_id) DO NOTHING;

WITH legacy_objects AS (
    SELECT *
    FROM health.sync_objects
    WHERE entity_type = 'daily_wellness_record'
      AND NOT deleted
), delete_events AS (
    INSERT INTO health.sync_events (
        event_id, device_id, entity_type, entity_id, dedupe_key, operation,
        schema_version, occurred_at, payload, submitted_operation, submitted_payload
    )
    SELECT
        md5(random()::text || clock_timestamp()::text)::uuid,
        device_id,
        entity_type,
        entity_id,
        dedupe_key,
        'delete',
        schema_version,
        now(),
        '{}'::jsonb,
        'delete',
        '{}'::jsonb
    FROM legacy_objects
    RETURNING *
)
INSERT INTO health.sync_objects (
    entity_type, entity_id, dedupe_key, latest_cursor, latest_event_id,
    device_id, operation, deleted, schema_version, occurred_at, updated_at, payload
)
SELECT
    entity_type, entity_id, dedupe_key, cursor, event_id,
    device_id, operation, true, schema_version, occurred_at, now(), payload
FROM delete_events
ON CONFLICT (entity_type, entity_id) DO UPDATE SET
    dedupe_key = EXCLUDED.dedupe_key,
    latest_cursor = EXCLUDED.latest_cursor,
    latest_event_id = EXCLUDED.latest_event_id,
    device_id = EXCLUDED.device_id,
    operation = 'delete',
    deleted = true,
    schema_version = EXCLUDED.schema_version,
    occurred_at = EXCLUDED.occurred_at,
    updated_at = EXCLUDED.updated_at,
    payload = '{}'::jsonb;

CREATE OR REPLACE VIEW health.workout_records AS
SELECT
    entity_id,
    payload->>'source' AS source,
    payload->>'workoutType' AS workout_type,
    payload->>'workoutCategory' AS workout_category,
    to_timestamp((payload->>'startAt')::double precision / 1000.0) AS start_at,
    (payload->>'durationSeconds')::integer AS duration_seconds,
    (payload->>'distanceMeters')::double precision AS distance_meters,
    (payload->>'caloriesKcal')::double precision AS calories_kcal,
    (payload->>'averageHeartRateBpm')::double precision AS average_heart_rate_bpm,
    (payload->>'maximumHeartRateBpm')::double precision AS maximum_heart_rate_bpm,
    (payload->>'averagePaceSecondsPerKm')::double precision AS average_pace_seconds_per_km,
    (payload->>'averagePaceSecondsPer100Meters')::double precision AS average_pace_seconds_per_100_meters,
    (payload->>'averageCadencePerMinute')::double precision AS average_cadence_per_minute,
    (payload->>'steps')::bigint AS steps,
    (payload->>'averageStrideCentimeters')::double precision AS average_stride_centimeters,
    (payload->>'elevationGainMeters')::double precision AS elevation_gain_meters,
    (payload->>'poolLengthMeters')::double precision AS pool_length_meters,
    (payload->>'lengths')::integer AS lengths,
    (payload->>'strokes')::integer AS strokes,
    (payload->>'averageSwolf')::double precision AS average_swolf,
    (payload->>'averageStrokeRatePerMinute')::double precision AS average_stroke_rate_per_minute,
    payload->>'mainStroke' AS main_stroke,
    (payload->>'confidence')::double precision AS confidence,
    occurred_at,
    updated_at
FROM health.sync_objects
WHERE entity_type = 'workout_record'
  AND NOT deleted;

COMMENT ON VIEW health.workout_records IS
    'Current structured running, walking, and swimming records imported from vivo Health share images.';

RESET ROLE;

GRANT USAGE ON SCHEMA health TO henji_sync;
GRANT SELECT ON health.workout_records TO henji_sync;

COMMIT;
