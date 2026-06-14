-- =============================================================================
-- V4  [v1.1] RLS lockout — block all DIRECT client access to the data tables.
--
-- Why this exists: every table in `public` is auto-exposed through Supabase's
-- PostgREST API, and the client ships the public anon key. With RLS OFF, that key
-- is a public handle to every row (read AND write), bypassing this backend
-- entirely. Our model is backend-only: clients use Supabase for AUTH ONLY and all
-- data goes through this API. So we lock the tables down.
--
-- How the lockout works: enable RLS and define NO policies. For a role that does
-- not bypass RLS (the `anon` / `authenticated` roles PostgREST uses), "RLS on +
-- no matching policy" = deny everything. So a direct PostgREST call with the anon
-- key sees nothing and can write nothing.
--
-- Why this backend is UNAFFECTED: it reaches Postgres over a direct JDBC
-- connection as the table-OWNER role (`postgres`), and the owner bypasses RLS by
-- default (we deliberately do NOT `FORCE ROW LEVEL SECURITY`). The Testcontainers
-- test DB connects as superuser, which also bypasses — so the suite is unaffected.
--
-- No REVOKE here on purpose: RLS-on-no-policy already denies anon/authenticated,
-- and those roles don't exist in the plain test Postgres (a REVOKE would error
-- there). The per-user / coach policies in schema.sql are intentionally NOT
-- applied — they stay as the spec for the ownership checks enforced in Kotlin
-- (and as a ready SDK-direct policy set if that model is ever adopted).
--
-- The [v1.5]/[v2] program & coaching tables get the same ENABLE in the migration
-- that creates them.
-- =============================================================================

ALTER TABLE exercises         ENABLE ROW LEVEL SECURITY;
ALTER TABLE workouts          ENABLE ROW LEVEL SECURITY;
ALTER TABLE workout_exercises ENABLE ROW LEVEL SECURITY;
ALTER TABLE sets              ENABLE ROW LEVEL SECURITY;
