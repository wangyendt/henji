BEGIN;

SET ROLE personal_knowledge_owner;

ALTER TABLE health.sync_events
    ADD COLUMN IF NOT EXISTS dedupe_key TEXT
    CHECK (dedupe_key IS NULL OR length(dedupe_key) BETWEEN 1 AND 255);
ALTER TABLE health.sync_objects
    ADD COLUMN IF NOT EXISTS dedupe_key TEXT
    CHECK (dedupe_key IS NULL OR length(dedupe_key) BETWEEN 1 AND 255);

UPDATE health.sync_events
SET dedupe_key = ((payload->>'measuredAt')::bigint / 60000)::text || ':' || round((payload->>'weightKg')::numeric * 100)::text
WHERE entity_type = 'weight_record'
  AND payload ? 'measuredAt'
  AND payload ? 'weightKg'
  AND dedupe_key IS NULL;

UPDATE health.sync_objects
SET dedupe_key = ((payload->>'measuredAt')::bigint / 60000)::text || ':' || round((payload->>'weightKg')::numeric * 100)::text
WHERE entity_type = 'weight_record'
  AND payload ? 'measuredAt'
  AND payload ? 'weightKg'
  AND dedupe_key IS NULL;

CREATE INDEX IF NOT EXISTS health_sync_objects_deleted_dedupe_idx
    ON health.sync_objects (entity_type, dedupe_key)
    WHERE deleted AND dedupe_key IS NOT NULL;

RESET ROLE;

COMMIT;
