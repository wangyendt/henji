BEGIN;

SET ROLE personal_knowledge_owner;

CREATE OR REPLACE VIEW health.daily_wellness_records AS
SELECT
    entity_id,
    DATE '1970-01-01' + (payload->>'dateEpochDay')::INTEGER AS record_date,
    payload->>'source' AS source,
    payload->>'screenType' AS screen_type,
    to_timestamp((payload->>'sleepStartAt')::DOUBLE PRECISION / 1000.0) AS sleep_start_at,
    to_timestamp((payload->>'sleepEndAt')::DOUBLE PRECISION / 1000.0) AS sleep_end_at,
    (payload->>'sleepMinutes')::INTEGER AS sleep_minutes,
    (payload->>'deepSleepMinutes')::INTEGER AS deep_sleep_minutes,
    (payload->>'lightSleepMinutes')::INTEGER AS light_sleep_minutes,
    (payload->>'remSleepMinutes')::INTEGER AS rem_sleep_minutes,
    (payload->>'awakeMinutes')::INTEGER AS awake_minutes,
    (payload->>'sleepScore')::DOUBLE PRECISION AS sleep_score,
    (payload->>'steps')::BIGINT AS steps,
    (payload->>'distanceMeters')::DOUBLE PRECISION AS distance_meters,
    (payload->>'activeCaloriesKcal')::DOUBLE PRECISION AS active_calories_kcal,
    (payload->>'exerciseMinutes')::INTEGER AS exercise_minutes,
    (payload->>'exerciseCaloriesKcal')::DOUBLE PRECISION AS exercise_calories_kcal,
    (payload->>'restingHeartRateBpm')::DOUBLE PRECISION AS resting_heart_rate_bpm,
    (payload->>'averageHeartRateBpm')::DOUBLE PRECISION AS average_heart_rate_bpm,
    COALESCE(payload->'workouts', '[]'::jsonb) AS workouts,
    (payload->>'confidence')::DOUBLE PRECISION AS confidence,
    occurred_at,
    updated_at
FROM health.sync_objects
WHERE entity_type = 'daily_wellness_record'
  AND NOT deleted;

COMMENT ON VIEW health.daily_wellness_records IS
    'Current sleep, activity, and workout facts extracted from shared vivo Health screenshots.';

RESET ROLE;

GRANT USAGE ON SCHEMA health TO henji_sync;
GRANT SELECT ON health.daily_wellness_records TO henji_sync;

COMMIT;
