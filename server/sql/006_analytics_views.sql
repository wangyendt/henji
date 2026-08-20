BEGIN;

SET ROLE personal_knowledge_owner;

CREATE OR REPLACE VIEW health.weight_records AS
SELECT
    entity_id,
    to_timestamp((payload->>'measuredAt')::double precision / 1000.0) AS measured_at,
    payload->>'source' AS source,
    payload->>'deviceName' AS device_name,
    (payload->>'weightKg')::double precision AS weight_kg,
    (payload->>'bmi')::double precision AS bmi,
    (payload->>'bodyFatPercent')::double precision AS body_fat_percent,
    (payload->>'bodyWaterPercent')::double precision AS body_water_percent,
    (payload->>'skeletalMusclePercent')::double precision AS skeletal_muscle_percent,
    (payload->>'bmrKcal')::integer AS bmr_kcal,
    (payload->>'fatFreeMassKg')::double precision AS fat_free_mass_kg,
    (payload->>'subcutaneousFatPercent')::double precision AS subcutaneous_fat_percent,
    (payload->>'visceralFat')::double precision AS visceral_fat,
    (payload->>'muscleMassKg')::double precision AS muscle_mass_kg,
    (payload->>'boneMassKg')::double precision AS bone_mass_kg,
    (payload->>'proteinPercent')::double precision AS protein_percent,
    (payload->>'bodyAge')::integer AS body_age,
    (payload->>'isEstimated')::boolean AS is_estimated,
    occurred_at,
    updated_at
FROM health.sync_objects
WHERE entity_type = 'weight_record' AND NOT deleted;

CREATE OR REPLACE VIEW health.meal_records AS
SELECT
    entity_id,
    to_timestamp((payload->>'createdAt')::double precision / 1000.0) AS meal_at,
    payload->>'mealType' AS meal_type,
    payload->>'foodNames' AS food_names,
    (payload->>'calorieLow')::integer AS calorie_low,
    (payload->>'calorieHigh')::integer AS calorie_high,
    (payload->>'proteinGrams')::double precision AS protein_grams,
    (payload->>'carbsGrams')::double precision AS carbs_grams,
    (payload->>'fatGrams')::double precision AS fat_grams,
    payload->>'advice' AS advice,
    (payload->>'confidence')::double precision AS confidence,
    occurred_at,
    updated_at
FROM health.sync_objects
WHERE entity_type = 'meal_record' AND NOT deleted;

CREATE OR REPLACE VIEW health.meal_foods AS
SELECT
    current.entity_id AS meal_id,
    food.ordinality::integer AS item_index,
    food.value->>'id' AS food_id,
    food.value->>'canonicalName' AS canonical_name,
    food.value->>'displayName' AS display_name,
    food.value->>'category' AS category,
    (food.value->>'estimatedGramsLow')::double precision AS estimated_grams_low,
    (food.value->>'estimatedGramsHigh')::double precision AS estimated_grams_high,
    (food.value->>'calorieLow')::double precision AS calorie_low,
    (food.value->>'calorieHigh')::double precision AS calorie_high,
    (food.value->>'confidence')::double precision AS confidence
FROM health.sync_objects AS current
CROSS JOIN LATERAL jsonb_array_elements(COALESCE(current.payload->'foods', '[]'::jsonb))
    WITH ORDINALITY AS food(value, ordinality)
WHERE current.entity_type = 'meal_record' AND NOT current.deleted;

COMMENT ON VIEW health.weight_records IS 'Typed current weight and body-composition records.';
COMMENT ON VIEW health.meal_records IS 'Typed current meal summaries; photos remain local to the phone.';
COMMENT ON VIEW health.meal_foods IS 'One typed row per structured food item in each current meal.';

RESET ROLE;

GRANT SELECT ON health.weight_records, health.meal_records, health.meal_foods TO henji_sync;

COMMIT;
