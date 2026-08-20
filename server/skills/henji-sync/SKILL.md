---
name: henji-sync
description: Read and explain Wayne's Henji synchronized health data in PostgreSQL, including weight/body composition, structured meals, running/walking/swimming workouts, tombstones, and synchronization history. Use whenever the user mentions Henji/衡迹, weight trends, food intake, calories, workouts, deleted records, or health sync state.
compatibility: Requires the psql-readonly command with SELECT access to the health schema.
---

# Henji Sync

Use PostgreSQL as the analytical source. The Henji HTTP API is a device synchronization protocol, not a reporting API.

## Data model

- `health.sync_objects` is the current effective state of every synchronized object.
- `health.sync_events` is the append-only synchronization and audit log.
- `deleted = true` is a permanent tombstone; exclude it from current-health answers unless deletion history is requested.
- `weight_record` contains `measuredAt`, `weightKg`, BMI, body fat, water, muscle, BMR and other body-composition fields. Prefer `health.weight_records` for analysis.
- `meal_record` contains meal time/type, calorie range, macro estimates, advice and structured `foods[]`. Prefer `health.meal_records` and `health.meal_foods`; photos stay on the phone and are not uploaded.
- `workout_record` contains a single running, walking or swimming session. Prefer the typed `health.workout_records` view for workout queries.
- `daily_wellness_record` is retired. Its remaining current object is a tombstone and its historical events exist only for audit.

## Safety boundary

- Query only through `psql-readonly`.
- Do not read API tokens or database writer passwords.
- Do not call `/v1/sync/push` or mutate health tables unless the user explicitly requests a separate write workflow.
- Do not present energy-to-weight conversion as measured fat change. It is an estimate; 7,700 kcal/kg is only a display convention.

## Common queries

Current types and live/deleted counts:

```sql
SELECT entity_type,
       count(*) FILTER (WHERE NOT deleted) AS live_count,
       count(*) FILTER (WHERE deleted) AS deleted_count
FROM health.sync_objects
GROUP BY entity_type
ORDER BY entity_type;
```

Recent representative raw weight objects:

```sql
SELECT measured_at, weight_kg, body_fat_percent, bmr_kcal
FROM health.weight_records
ORDER BY measured_at DESC
LIMIT 30;
```

Structured meals:

```sql
SELECT meal_at, meal_type, food_names, calorie_low, calorie_high,
       protein_grams, carbs_grams, fat_grams
FROM health.meal_records
ORDER BY meal_at DESC;
```

Running, walking and swimming:

```sql
SELECT start_at, workout_type, workout_category, duration_seconds,
       distance_meters, calories_kcal, average_heart_rate_bpm,
       steps, lengths, strokes, average_swolf
FROM health.workout_records
ORDER BY start_at DESC;
```

Inspect `health.sync_events` when the user asks which device changed an item, why duplicates occurred, or why deletion won over a stale update.

## Analysis practice

- State date range, record count, units and excluded tombstones.
- Distinguish measured scale change from calorie-derived estimates.
- Flag missing meals before computing energy balance; absence of a meal record is not zero intake.
- Treat body-composition and calorie estimates as trend data rather than medical diagnosis.
