-- =============================================================================
-- Workout Tracker — Postgres / Supabase schema
-- =============================================================================
-- Design summary (decisions made during planning):
--   * Plan side mirrors actual side 1-to-1 (same measurement columns).
--   * measurement_type lives on `exercises`, denormalized onto set tables so a
--     plain column CHECK (Option B) can validate each row.
--   * Individual set rows on BOTH sides (a "3x5" prescription = 3 program_set
--     rows), so plan<->actual links are clean 1-to-1.
--   * user_id + exercise_id + measurement_type denormalized onto set tables for
--     flat, indexed analytics (bidirectional weight/reps history).
--   * Nullable RPE on both set tables: programmable and trackable, optional.
--   * Coaching modeled via a coach_athlete relationship table, not a column.
--   * Auth: users come from Supabase `auth.users`; we reference auth.uid().
--
-- Build stages are marked [v1], [v1.5], [v2]. You can apply only the v1 blocks
-- first and add later stages as additive migrations — nothing here gets rewritten.
-- =============================================================================


-- =============================================================================
-- [v1]  ENUMS & CATALOG
-- =============================================================================

-- Shape of measurement for an exercise. Drives which set columns are required.
CREATE TYPE measurement_type AS ENUM (
  'weight_reps',          -- squat, bench: weight + reps
  'bodyweight',           -- pull-up, push-up: reps only
  'weighted_bodyweight',  -- weighted dip/pull-up: added_weight + reps
  'duration',             -- plank, dead hang: duration only
  'distance_time'         -- run, row: distance (+ optional duration)
);

-- The exercise catalog. Referenced by both the plan and the actual side, which
-- is what makes the two comparable. Seed a global set; optionally allow custom.
CREATE TABLE exercises (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name              text NOT NULL,
  category          text,                       -- e.g. 'lower', 'push', 'cardio'
  measurement_type  measurement_type NOT NULL,
  -- owner is NULL for global/built-in exercises, set for user-created ones [v1.1]
  created_by        uuid REFERENCES auth.users (id) ON DELETE SET NULL,
  created_at        timestamptz NOT NULL DEFAULT now()
);

-- A user shouldn't see duplicate names within their own custom set; global
-- names are unique among globals. (Adjust if you want stricter global naming.)
CREATE UNIQUE INDEX exercises_unique_global_name
  ON exercises (lower(name)) WHERE created_by IS NULL;
-- [v1.1] ... and the same per user for their own exercises. A user may still
-- shadow a global name; the catalog name search is the guard against that.
CREATE UNIQUE INDEX exercises_unique_user_name
  ON exercises (created_by, lower(name)) WHERE created_by IS NOT NULL;


-- =============================================================================
-- [v1]  ACTUAL SIDE — the log
-- =============================================================================

-- A training session that actually happened.
CREATE TABLE workouts (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  performed_at        timestamptz NOT NULL DEFAULT now(),
  notes               text,
  -- [v1.5] link back to the program session this workout was performed from.
  -- Nullable: freestyle workouts leave it NULL. FK added in the v1.5 block below.
  program_workout_id  uuid,
  created_at          timestamptz NOT NULL DEFAULT now()
);

-- "In this workout I did this exercise." Allows ordering and per-exercise notes,
-- and keeps the door open for supersets later.
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


-- =============================================================================
-- [v1.5]  PLAN SIDE — programs (mirrors the actual side)
-- =============================================================================

-- A named plan. owner builds it; athlete follows it. For self-coached users,
-- owner_id = athlete_id. Keeping them separate from the start means the v2
-- coaching layer needs no restructure — only new RLS policies.
CREATE TABLE programs (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id    uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  athlete_id  uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  name        text NOT NULL,
  notes       text,
  created_at  timestamptz NOT NULL DEFAULT now()
);

-- Planned sessions, positioned by week/day rather than a calendar date, so a
-- program is a reusable template rather than pinned to specific dates.
CREATE TABLE program_workouts (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  program_id  uuid NOT NULL REFERENCES programs (id) ON DELETE CASCADE,
  week        int  NOT NULL DEFAULT 1,
  day         int  NOT NULL DEFAULT 1,
  name        text,
  position    int  NOT NULL DEFAULT 0
);

-- Prescribed exercises within a planned session. References the same catalog.
CREATE TABLE program_exercises (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  program_workout_id  uuid NOT NULL REFERENCES program_workouts (id) ON DELETE CASCADE,
  exercise_id         uuid NOT NULL REFERENCES exercises (id),
  position            int  NOT NULL DEFAULT 0,
  notes               text
);

-- Prescribed target sets — same shape as `sets`. "3x5" = 3 rows here.
CREATE TABLE program_sets (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  program_exercise_id  uuid NOT NULL REFERENCES program_exercises (id) ON DELETE CASCADE,
  exercise_id          uuid NOT NULL REFERENCES exercises (id),
  measurement_type     measurement_type NOT NULL,
  set_index            int  NOT NULL DEFAULT 0,

  -- target measurement columns (mirror of sets)
  weight            numeric(7,3),
  reps              int,
  added_weight      numeric(7,3),
  duration_seconds  int,
  distance_meters   numeric(10,3),
  rpe               numeric(3,1),   -- programmed RPE, optional

  CONSTRAINT program_sets_measurement_valid CHECK (
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
    END
  ),
  CONSTRAINT program_sets_rpe_range CHECK (rpe IS NULL OR (rpe >= 0 AND rpe <= 10))
);

CREATE INDEX program_workouts_program ON program_workouts (program_id);
CREATE INDEX program_exercises_pw     ON program_exercises (program_workout_id);
CREATE INDEX program_sets_pe          ON program_sets (program_exercise_id);

-- ---- Now wire the actual side back to the plan side (the link FKs) ----
ALTER TABLE workouts
  ADD CONSTRAINT workouts_program_workout_fk
  FOREIGN KEY (program_workout_id) REFERENCES program_workouts (id) ON DELETE SET NULL;

ALTER TABLE sets
  ADD CONSTRAINT sets_program_set_fk
  FOREIGN KEY (program_set_id) REFERENCES program_sets (id) ON DELETE SET NULL;


-- =============================================================================
-- [v2]  COACHING RELATIONSHIP
-- =============================================================================

CREATE TYPE coach_link_status AS ENUM ('pending', 'active', 'ended');

-- Explicit relationship table: a coach is linked to an athlete. Supports many
-- athletes per coach, switching coaches, invitations, and a coach who is also
-- someone else's athlete. "coach" / "athlete" are roles, not user types.
CREATE TABLE coach_athlete (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  coach_id    uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  athlete_id  uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  status      coach_link_status NOT NULL DEFAULT 'pending',
  created_at  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT coach_athlete_not_self CHECK (coach_id <> athlete_id),
  CONSTRAINT coach_athlete_unique UNIQUE (coach_id, athlete_id)
);

CREATE INDEX coach_athlete_by_coach   ON coach_athlete (coach_id, status);
CREATE INDEX coach_athlete_by_athlete ON coach_athlete (athlete_id, status);

-- Helper: is the current user an active coach of the given athlete?
CREATE OR REPLACE FUNCTION is_active_coach_of(target_athlete uuid)
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM coach_athlete ca
    WHERE ca.coach_id = auth.uid()
      AND ca.athlete_id = target_athlete
      AND ca.status = 'active'
  );
$$;


-- =============================================================================
-- ROW LEVEL SECURITY
-- =============================================================================
-- Enable RLS on everything that holds user data. If you instead front the DB
-- with a backend on the service role (which BYPASSES RLS), replicate these same
-- checks in application code — the logic is identical.

ALTER TABLE exercises         ENABLE ROW LEVEL SECURITY;
ALTER TABLE workouts          ENABLE ROW LEVEL SECURITY;
ALTER TABLE workout_exercises ENABLE ROW LEVEL SECURITY;
ALTER TABLE sets              ENABLE ROW LEVEL SECURITY;
ALTER TABLE programs          ENABLE ROW LEVEL SECURITY;
ALTER TABLE program_workouts  ENABLE ROW LEVEL SECURITY;
ALTER TABLE program_exercises ENABLE ROW LEVEL SECURITY;
ALTER TABLE program_sets      ENABLE ROW LEVEL SECURITY;
ALTER TABLE coach_athlete     ENABLE ROW LEVEL SECURITY;

-- ---- [v1] exercises: everyone reads global + their own; writes own only ----
CREATE POLICY exercises_read ON exercises FOR SELECT
  USING (created_by IS NULL OR created_by = auth.uid());
CREATE POLICY exercises_write_own ON exercises FOR ALL
  USING (created_by = auth.uid())
  WITH CHECK (created_by = auth.uid());

-- ---- [v1] workouts / workout_exercises / sets: own data ----
-- (the [v2] coach read access is added as additional policies further down)
CREATE POLICY workouts_own ON workouts FOR ALL
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

CREATE POLICY workout_exercises_own ON workout_exercises FOR ALL
  USING (EXISTS (SELECT 1 FROM workouts w
                 WHERE w.id = workout_exercises.workout_id
                   AND w.user_id = auth.uid()))
  WITH CHECK (EXISTS (SELECT 1 FROM workouts w
                      WHERE w.id = workout_exercises.workout_id
                        AND w.user_id = auth.uid()));

CREATE POLICY sets_own ON sets FOR ALL
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

-- ---- [v1.5] programs: self-authored (owner = athlete) ----
-- These USING clauses already allow owner access; v2 coaching works because a
-- coach is simply the owner of a program whose athlete_id is someone else.
CREATE POLICY programs_owner_or_athlete_read ON programs FOR SELECT
  USING (owner_id = auth.uid() OR athlete_id = auth.uid());
CREATE POLICY programs_owner_write ON programs FOR ALL
  USING (owner_id = auth.uid())
  WITH CHECK (
    owner_id = auth.uid()
    -- you may only assign an athlete that is yourself, or [v2] one you coach
    AND (athlete_id = auth.uid() OR is_active_coach_of(athlete_id))
  );

-- program children inherit access from their parent program
CREATE POLICY program_workouts_access ON program_workouts FOR ALL
  USING (EXISTS (SELECT 1 FROM programs p
                 WHERE p.id = program_workouts.program_id
                   AND (p.owner_id = auth.uid() OR p.athlete_id = auth.uid())))
  WITH CHECK (EXISTS (SELECT 1 FROM programs p
                      WHERE p.id = program_workouts.program_id
                        AND p.owner_id = auth.uid()));

CREATE POLICY program_exercises_access ON program_exercises FOR ALL
  USING (EXISTS (SELECT 1 FROM program_workouts pw
                 JOIN programs p ON p.id = pw.program_id
                 WHERE pw.id = program_exercises.program_workout_id
                   AND (p.owner_id = auth.uid() OR p.athlete_id = auth.uid())))
  WITH CHECK (EXISTS (SELECT 1 FROM program_workouts pw
                      JOIN programs p ON p.id = pw.program_id
                      WHERE pw.id = program_exercises.program_workout_id
                        AND p.owner_id = auth.uid()));

CREATE POLICY program_sets_access ON program_sets FOR ALL
  USING (EXISTS (SELECT 1 FROM program_exercises pe
                 JOIN program_workouts pw ON pw.id = pe.program_workout_id
                 JOIN programs p ON p.id = pw.program_id
                 WHERE pe.id = program_sets.program_exercise_id
                   AND (p.owner_id = auth.uid() OR p.athlete_id = auth.uid())))
  WITH CHECK (EXISTS (SELECT 1 FROM program_exercises pe
                      JOIN program_workouts pw ON pw.id = pe.program_workout_id
                      JOIN programs p ON p.id = pw.program_id
                      WHERE pe.id = program_sets.program_exercise_id
                        AND p.owner_id = auth.uid()));

-- ---- [v2] coach_athlete: each party can see links they're part of ----
CREATE POLICY coach_athlete_visible ON coach_athlete FOR SELECT
  USING (coach_id = auth.uid() OR athlete_id = auth.uid());
-- a coach proposes the link; the athlete accepts (status -> active)
CREATE POLICY coach_athlete_coach_manage ON coach_athlete FOR ALL
  USING (coach_id = auth.uid())
  WITH CHECK (coach_id = auth.uid());

-- ---- [v2] coach READ access to their active athletes' logs ----
-- Added as SEPARATE policies (RLS policies are OR-ed together), so v1 "own data"
-- policies stay untouched and this is purely additive.
CREATE POLICY workouts_coach_read ON workouts FOR SELECT
  USING (is_active_coach_of(user_id));
CREATE POLICY sets_coach_read ON sets FOR SELECT
  USING (is_active_coach_of(user_id));
CREATE POLICY workout_exercises_coach_read ON workout_exercises FOR SELECT
  USING (EXISTS (SELECT 1 FROM workouts w
                 WHERE w.id = workout_exercises.workout_id
                   AND is_active_coach_of(w.user_id)));


-- =============================================================================
-- EXAMPLE ANALYTICS QUERIES (not part of the schema; here as a reference)
-- =============================================================================
-- 1) Reps history at a given weight for one exercise:
--      SELECT performed_at, reps FROM sets
--      WHERE user_id = auth.uid() AND exercise_id = $1 AND weight = $2
--      ORDER BY performed_at;
--
-- 2) Weight history at a given rep count for one exercise:
--      SELECT performed_at, weight FROM sets
--      WHERE user_id = auth.uid() AND exercise_id = $1 AND reps = $2
--      ORDER BY performed_at;
--
-- 3) Adherence for a program workout (planned vs performed, 1-to-1 via link):
--      SELECT ps.id AS planned, s.id AS actual,
--             ps.weight AS tgt_w, s.weight AS act_w,
--             ps.reps   AS tgt_r, s.reps   AS act_r
--      FROM program_sets ps
--      LEFT JOIN sets s ON s.program_set_id = ps.id
--      WHERE ps.program_exercise_id = $1;     -- missed sets show actual = NULL
-- =============================================================================
