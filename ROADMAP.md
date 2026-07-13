# Roadmap & status

Current status and remaining work for workout-api. See also: design rationale in
[`workout-tracker-plan.md`](workout-tracker-plan.md), the staged DDL in [`schema.sql`](schema.sql),
run/usage + endpoints in [`README.md`](README.md), coding patterns/guardrails in
[`CLAUDE.md`](CLAUDE.md) + [`.claude/rules/`](.claude/rules), and forward-looking, not-yet-built
decisions (hosting, offline-first, guest accounts) in [`planning-notes.md`](planning-notes.md).

## Done — v1 (shipped)

Auth, exercise catalog, logging, and bidirectional history — all owner-scoped and tested
(authorization tests on every user-data path; problem+json errors; Konsist architecture rules;
`./gradlew check` green). Plus hardening/polish: tightened validation, paginated list responses,
OpenAPI/Swagger UI, and a Dockerfile.

- **M0** scaffold · **M1** local DB + Flyway · **M2** Supabase ES256 JWT auth ·
  **M3** exercise catalog · **M4** logging (workouts → workout_exercises → sets,
  measurement-validated) · **M5** bidirectional history · **M6** hardening (problem+json,
  real ES256 decoder test, Swagger UI, Dockerfile).

## Done — v1.1: user-created exercises + guest accounts (shipped)

**User-created exercises:**
- `POST /api/exercises` sets `created_by = me`; all reads (list, by-id, history, and the
  exercise lookups inside workout/set logging) are scoped to **global OR mine**.
- **Name search** on `GET /api/exercises` (`?name=`, case-insensitive substring), so the
  client searches before offering "create new" — the defense against catalog fragmentation.
- `V3` **partial unique index** on `(created_by, lower(name)) WHERE created_by IS NOT NULL`;
  duplicate (per user) → `409`; client picks `measurement_type` on create.

**Guest accounts + member-gating** (Supabase anonymous sign-ins):
- `enable_anonymous_sign_ins = true` in `supabase/config.toml` (config change needs a local
  stack restart). The client calls `signInAnonymously()` → a real `auth.users` row, role
  `authenticated`, `is_anonymous: true`. Converting later (sign up / link identity) keeps
  the same user id + data.
- `SupabaseRoleConverter` maps the `is_anonymous` claim to `ROLE_GUEST`/`ROLE_MEMBER`
  (claim missing → guest, mirroring Supabase's own RLS example); `POST /api/exercises`
  requires `MEMBER`. Guests keep read + logging (workouts/sets stay `authenticated`).
- Tested: guest → 403 on create (also with the claim absent), member → 201, guests still
  browse + log. Manual check: `scripts/dev-token.sh --guest`.

## Next — v1.5: self-authored programs (the plan side)

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
- **RLS:** ✅ **lockout shipped** (`V4`). Every public data table has RLS enabled with **no
  policies**, so the client's public anon key can't reach them via Supabase's PostgREST —
  all data goes through this backend (which bypasses RLS as table owner). Guarded by
  `RlsLockoutTest`. The per-user/coach policies in `schema.sql` stay as the *spec* for the
  in-code ownership checks (and an SDK-direct fallback), and are **not** deployed.
  Residual at cloud-deploy: flip Supabase's project-level "Enable RLS on new tables" toggle
  so future tables are locked by default.
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
