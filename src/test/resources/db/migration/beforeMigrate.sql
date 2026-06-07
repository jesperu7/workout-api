-- =============================================================================
-- TEST-ONLY Flyway callback (lives on the test classpath only).
--
-- Runs before migrations during tests. Vanilla Postgres (Testcontainers) has no
-- Supabase `auth` schema, but V1 FKs to auth.users(id). This creates a minimal
-- stand-in so the *real* production migrations apply unchanged against a plain
-- postgres image — we never fork the schema for tests.
--
-- Idempotent: beforeMigrate runs on every migrate.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
  id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email text
);

-- Supabase exposes the current user id via auth.uid(). The backend never relies
-- on it (it passes the user id from the validated JWT explicitly), but stubbing
-- it keeps any future auth.uid()-referencing SQL from erroring under test.
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid
  LANGUAGE sql STABLE
  AS $$ SELECT NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
