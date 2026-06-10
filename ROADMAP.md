# Roadmap & status

Current status and remaining work for workout-api. See also: design rationale in
[`workout-tracker-plan.md`](workout-tracker-plan.md), the staged DDL in [`schema.sql`](schema.sql),
run/usage + endpoints in [`README.md`](README.md), and coding patterns/guardrails in
[`CLAUDE.md`](CLAUDE.md) + [`.claude/rules/`](.claude/rules).

## Done — v1 (shipped)

Auth, exercise catalog, logging, and bidirectional history — all owner-scoped and tested
(authorization tests on every user-data path; problem+json errors; Konsist architecture rules;
`./gradlew check` green). Plus hardening/polish: tightened validation, paginated list responses,
OpenAPI/Swagger UI, and a Dockerfile.

- **M0** scaffold · **M1** local DB + Flyway · **M2** Supabase ES256 JWT auth ·
  **M3** exercise catalog · **M4** logging (workouts → workout_exercises → sets,
  measurement-validated) · **M5** bidirectional history · **M6** hardening (problem+json,
  real ES256 decoder test, Swagger UI, Dockerfile).

## Next — v1.1: user-created exercises + guest accounts (small; do before/with the client)

Let users add their own exercises, and introduce guest (anonymous) accounts — gating
exercise creation to real (signed-in) users.

**User-created exercises** (the schema's `created_by` column is already there):
- `POST /api/exercises` sets `created_by = me`; reads return **global OR mine** (the filter
  `ExerciseService` already has a TODO for).
- Add **name search** to `GET /api/exercises`, so the client searches before offering
  "create new" — the real defense against catalog fragmentation (`schema.sql` §3.1).
- New **partial unique index** on `(created_by, lower(name)) WHERE created_by IS NOT NULL`
  (today's index only covers globals); client picks `measurement_type` on create.

**Guest accounts + member-gating** (Supabase anonymous sign-ins):
- Enable `enable_anonymous_sign_ins` in `supabase/config.toml` (currently off). The client
  calls `signInAnonymously()` → a real `auth.users` row, role `authenticated`,
  `is_anonymous: true`. Converting later (sign up / link identity) keeps the same user id + data.
- BE gate: a `JwtAuthenticationConverter` grants `ROLE_MEMBER` when `is_anonymous` is false,
  else `ROLE_GUEST`; then `POST /api/exercises` requires `hasRole("MEMBER")`. Guests keep
  read + logging (workouts/sets stay `authenticated`).
- Tests: guest token → 403 on create; member token → 201.

## Then — v1.5: self-authored programs (the plan side)

Mirror the actual side with the prescribed/plan side, then link them. Build one feature slice
at a time (Controller → Service → Repository → model/dto), following the existing `workout/`
slice as the template.

1. **Migration** (`V3+`, additive — never edit `V1`/`V2`): create `programs`,
   `program_workouts`, `program_exercises`, `program_sets` from the `[v1.5]` blocks of
   `schema.sql`, **and** add the plan↔actual FK constraints
   (`workouts.program_workout_id → program_workouts`, `sets.program_set_id → program_sets`;
   the nullable columns already exist).
2. **Program CRUD** in a new `program/` package: programs + nested program_workouts /
   program_exercises / program_sets, owner-scoped (owner_id = athlete_id for now).
   **Reuse `measurement/MeasurementValidator`** for `program_sets` (identical matrix).
3. **Link logging to plans:** let a workout reference a `program_workout` and a set reference a
   `program_set` (validating ownership of the referenced plan rows).
4. **Adherence read:** planned-vs-performed (`schema.sql` §6 query 3),
   e.g. `GET /api/programs/{id}/adherence`.
5. **Tests:** owner-scoping/authorization, measurement validation on program_sets, and a
   planned-vs-actual adherence test.

## Later — v2: coaching

- Add `coach_athlete` + the `is_active_coach_of()` helper (the `[v2]` blocks of `schema.sql`).
- Coach READ access to athletes' logs — additive, OR-ed onto the owner checks (in code).
- Programs may have `owner_id ≠ athlete_id`. **Test coach isolation hardest**: a coach must
  never see another coach's athletes (a real privacy bug).

## Deferred / cross-cutting (do when relevant)

- **Cloud + deploy:** create the Supabase cloud project, confirm/rotate to ES256, add the
  `local`/`prod` Spring profile split (env-var homes already wired in `application.yml`), then
  deploy the image. (An auto-memory note tracks this.)
- **RLS:** the policies in `schema.sql` are a *spec* enforced in Kotlin (this service-role
  backend bypasses RLS). Apply them as an additive migration only if a direct-to-Postgres
  client (e.g. the Supabase SDK) is ever introduced.
- **detekt:** add once it ships a stable Kotlin 2.2 release (parked; Spotless/ktlint + Konsist
  are already in).
- **Optional:** richer Konsist layering rules now that real feature code exists (e.g. "a
  controller must not hold a `*Repository`"); request logging / metrics; pagination on the
  nested `sets` list if histories grow large.

## Working agreement (keep the bar)

- Build only the current stage's scope; the schema is staged `[v1]` / `[v1.5]` / `[v2]`.
- Read the relevant `.claude/rules/*.md` before writing code, and match the existing slices.
- Every user-data path is owner-scoped with an authorization test; SQL lives only in
  repositories (named params); errors are problem+json; `./gradlew check` stays green.
