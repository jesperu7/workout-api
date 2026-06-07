-- =============================================================================
-- V1  [v1] Logging core: enum + catalog + actual-side log tables.
-- Transcribed faithfully from the [v1] blocks of /schema.sql (the design doc).
--
-- Deliberately NOT included yet (see workout-tracker-plan.md roadmap):
--   * The [v1.5] plan tables (programs/...) and the plan<->actual FK constraints.
--     The nullable link COLUMNS (program_workout_id, program_set_id) ARE created
--     now, as the plan prescribes, so adding the FKs later is a no-rewrite migration.
--   * RLS policies. This service-role backend bypasses RLS and enforces ownership
--     in Kotlin (the RLS section of schema.sql is the spec for those checks). RLS
--     keys off auth.uid(), which is NULL over a plain JDBC connection, so enabling
--     it here would either be inert (service role) or block all rows. Add it as an
--     additive migration only if a direct-to-Postgres client is ever introduced.
--
-- FKs reference Supabase's auth.users. Real envs (and the local Supabase stack)
-- provide it; the Testcontainers test DB gets a minimal stand-in via
-- src/test/resources/db/migration/beforeMigrate.sql.
-- =============================================================================

-- Shape of measurement for an exercise. Drives which set columns are required.
CREATE TYPE measurement_type AS ENUM (
  'weight_reps',          -- squat, bench: weight + reps
  'bodyweight',           -- pull-up, push-up: reps only
  'weighted_bodyweight',  -- weighted dip/pull-up: added_weight + reps
  'duration',             -- plank, dead hang: duration only
  'distance_time'         -- run, row: distance (+ optional duration)
);

-- The exercise catalog. Referenced by both the plan and the actual side.
CREATE TABLE exercises (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name              text NOT NULL,
  category          text,                       -- e.g. 'lower', 'push', 'cardio'
  measurement_type  measurement_type NOT NULL,
  -- NULL for global/built-in exercises, set for user-created ones [v1.5+]
  created_by        uuid REFERENCES auth.users (id) ON DELETE SET NULL,
  created_at        timestamptz NOT NULL DEFAULT now()
);

-- Global names unique among globals (case-insensitive).
CREATE UNIQUE INDEX exercises_unique_global_name
  ON exercises (lower(name)) WHERE created_by IS NULL;

-- A training session that actually happened.
CREATE TABLE workouts (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  performed_at        timestamptz NOT NULL DEFAULT now(),
  notes               text,
  -- [v1.5] link back to the planned session. Nullable; FK added with the plan tables.
  program_workout_id  uuid,
  created_at          timestamptz NOT NULL DEFAULT now()
);

-- "In this workout I did this exercise." Ordering + per-exercise notes.
CREATE TABLE workout_exercises (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workout_id   uuid NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
  exercise_id  uuid NOT NULL REFERENCES exercises (id),
  position     int  NOT NULL DEFAULT 0,
  notes        text
);

-- The heart of the app: one row per performed set.
-- user_id, exercise_id, measurement_type are denormalized (derived from the
-- parent chain / exercise) for flat indexed analytics and a self-contained CHECK.
CREATE TABLE sets (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workout_exercise_id  uuid NOT NULL REFERENCES workout_exercises (id) ON DELETE CASCADE,
  user_id              uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  exercise_id          uuid NOT NULL REFERENCES exercises (id),
  measurement_type     measurement_type NOT NULL,
  set_index            int  NOT NULL DEFAULT 0,
  performed_at         timestamptz NOT NULL DEFAULT now(),

  -- measurement columns (all nullable; CHECK enforces the right combo per type)
  weight            numeric(7,3),   -- canonical unit: kilograms. numeric, NOT float.
  reps              int,
  added_weight      numeric(7,3),   -- for weighted_bodyweight
  duration_seconds  int,
  distance_meters   numeric(10,3),
  rpe               numeric(3,1),   -- optional, any type, e.g. 8.5

  -- [v1.5] which prescribed target (if any) this set fulfilled. Nullable.
  program_set_id    uuid,

  CONSTRAINT sets_measurement_valid CHECK (
    CASE measurement_type
      WHEN 'weight_reps' THEN
        weight IS NOT NULL AND reps IS NOT NULL
        AND added_weight IS NULL AND duration_seconds IS NULL AND distance_meters IS NULL
      WHEN 'bodyweight' THEN
        reps IS NOT NULL
        AND weight IS NULL AND added_weight IS NULL
        AND duration_seconds IS NULL AND distance_meters IS NULL
      WHEN 'weighted_bodyweight' THEN
        added_weight IS NOT NULL AND reps IS NOT NULL
        AND weight IS NULL AND duration_seconds IS NULL AND distance_meters IS NULL
      WHEN 'duration' THEN
        duration_seconds IS NOT NULL
        AND weight IS NULL AND reps IS NULL AND added_weight IS NULL AND distance_meters IS NULL
      WHEN 'distance_time' THEN
        distance_meters IS NOT NULL
        AND weight IS NULL AND reps IS NULL AND added_weight IS NULL
      -- duration_seconds optional for distance_time (pace), so not constrained here
    END
  ),
  CONSTRAINT sets_rpe_range CHECK (rpe IS NULL OR (rpe >= 0 AND rpe <= 10))
);

-- ---- Indexes for the two headline history queries ----
-- "reps history at a given weight, for one of my exercises"
CREATE INDEX sets_user_exercise_weight ON sets (user_id, exercise_id, weight);
-- "weight history at a given rep count, for one of my exercises"
CREATE INDEX sets_user_exercise_reps   ON sets (user_id, exercise_id, reps);
-- general "my history for this exercise over time"
CREATE INDEX sets_user_exercise_time   ON sets (user_id, exercise_id, performed_at);
CREATE INDEX sets_program_set          ON sets (program_set_id);
