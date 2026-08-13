BEGIN;

SET ROLE personal_knowledge_owner;

UPDATE health.sync_events
SET dedupe_key = (split_part(dedupe_key, ':', 1)::bigint / 60000)::text
    || ':' || split_part(dedupe_key, ':', 2)
WHERE entity_type = 'weight_record'
  AND dedupe_key IS NOT NULL
  AND split_part(dedupe_key, ':', 1)::bigint > 10000000000;

UPDATE health.sync_objects
SET dedupe_key = (split_part(dedupe_key, ':', 1)::bigint / 60000)::text
    || ':' || split_part(dedupe_key, ':', 2)
WHERE entity_type = 'weight_record'
  AND dedupe_key IS NOT NULL
  AND split_part(dedupe_key, ':', 1)::bigint > 10000000000;

RESET ROLE;

COMMIT;
