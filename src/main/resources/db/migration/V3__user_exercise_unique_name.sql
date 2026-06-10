-- =============================================================================
-- V3  [v1.1] User-created exercises: per-user unique names.
--
-- `exercises.created_by` exists since V1 (NULL = global). This adds the
-- uniqueness the design doc promises for the user-created partition: names
-- unique within ONE user's own exercises (case-insensitive), mirroring
-- `exercises_unique_global_name` for globals. The two partial indexes don't
-- cross-check, so a user MAY shadow a global name — the name search on
-- GET /api/exercises is the intended defense against duplicating globals.
-- =============================================================================

CREATE UNIQUE INDEX exercises_unique_user_name
  ON exercises (created_by, lower(name)) WHERE created_by IS NOT NULL;
