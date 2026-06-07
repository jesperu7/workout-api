# Workout Tracker — Project Plan & Handoff

> Planning document capturing architecture and data-model decisions for a
> personal workout-tracking app. Intended as a handoff to continue the build in
> Claude Code. Pairs with `schema.sql` (the full Postgres/Supabase DDL).

---

## 1. Product summary

A workout-tracking app with three core capabilities:

1. **Logging** — record workouts, exercises, and individual sets. Headline
   feature: **bidirectional history** — for a given exercise, see rep history at
   a chosen weight *and* weight history at a chosen rep count.
2. **Programs** — set up a plan of prescribed workouts/exercises/sets that a
   user follows, then track planned-vs-actual performance.
3. **Coaching** — a coach can build programs for an athlete and view that
   athlete's workouts. A program author may be the user themselves (self-coached)
   or a separate coach.

A **web dashboard** is where programs are authored (by a coach or the user).
A **mobile app** handles day-to-day logging.

---

## 2. Architecture

### 2.1 Components

```
Mobile app (Flutter or native — UNDECIDED, and intentionally not a factor
            in backend choice)
   │  Supabase client SDK: sign in -> receives JWT
   ▼
Backend  (stack UNDECIDED: .NET, Java/Kotlin, or Go)
   │  • validates Supabase JWT locally (JWKS)
   │  • business logic + ownership checks
   ▼
Supabase:  Postgres  +  Auth  +  Storage
```

### 2.2 Auth flow

- Supabase Auth (hosted GoTrue) handles sign-in and issues a **JWT access
  token** + refresh token to the client.
- The client sends the JWT as `Authorization: Bearer <token>` to the backend.
- The backend **validates the JWT locally** and reads the user UUID from the
  `sub` claim. The backend never handles passwords.

### 2.3 JWT verification — IMPORTANT current details (verified June 2026)

- **Use asymmetric signing keys (ES256/RS256), not the legacy shared secret.**
  Asymmetric signing is now GA for all Supabase projects.
- The backend verifies tokens **locally** using public keys from the JWKS
  discovery endpoint — no round-trip to the Auth server per request:
  ```
  https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
  ```
- Standard pattern: fetch JWKS on startup, cache public keys in memory, read the
  `kid` from each incoming JWT, verify locally; re-fetch only when an unknown
  `kid` appears (i.e. after key rotation).
- **Gotchas:**
  - Check which algorithm the project actually issues — older projects may still
    default to HS256. Create the project and **rotate to an asymmetric key** in
    the dashboard before wiring the backend.
  - The JWKS endpoint returns **no keys** if the project is still on a shared
    HS256 secret.
  - JWKS is edge-cached ~10 min; a multi-level cache clears ~every 20 min. When
    rotating/revoking keys, wait ≥20 min to avoid rejecting valid tokens. Expect
    propagation delay on urgent revocation.
  - New API key format: `sb_publishable_…` (client) and `sb_secret_…` (backend),
    replacing the old `anon` / `service_role` keys. Keep the secret key
    server-side only.
  - Default JWT expiry is short (~5 min) — rely on the client SDK's refresh-token
    handling.

### 2.4 Backend stack — still OPEN (.NET vs Java/Kotlin vs Go)

All three validate Supabase JWTs the same way and talk to Postgres cleanly. None
is a wrong choice. Decision axes:

| Axis | .NET | Java/Kotlin (Spring) | Go |
|------|------|----------------------|-----|
| JWT/auth integration | Batteries-included (`JwtBearer`, point at JWKS) | Batteries-included (`jwk-set-uri` config) | Manual ~20 lines (`golang-jwt` + `keyfunc`) |
| DB / ORM | EF Core, Dapper | Hibernate/JPA, jOOQ, Exposed | `pgx` + `sqlc`, or GORM |
| Storage SDK | none first-class (use S3/REST API) | none first-class | none first-class |
| Runtime cost / deploy | Lean (esp. AOT) | Heaviest (unless GraalVM native) | Leanest — single static binary, tiny footprint |
| Concurrency | async/await (mature) | virtual threads (Loom) | goroutines (most ergonomic) |

**Tiebreakers if starting even:** Go for leanest/cheapest deploy; .NET for
richest out-of-the-box framework + still-lean runtime; Kotlin if there's any
chance of native Android (shared language). **If you already know one well, that
familiarity outweighs all of the above at this scale.**

> Note: mobile framework choice deliberately does NOT influence the backend
> decision.

### 2.5 DB access pattern + RLS decision

Two options for reaching Postgres/Storage from the backend:

1. **Direct Postgres connection** (idiomatic for a custom backend) — full SQL,
   migrations, ORM of choice. Typically connects with the **service role**,
   which **BYPASSES Row Level Security**, so ownership/authorization must be
   enforced in application code.
2. **Through Supabase REST (PostgREST) / Storage APIs** — less common when you
   already have a backend.

**Key decision still implicitly open:** SDK-direct-with-RLS vs.
backend-with-service-role.
- If clients hit Supabase directly → **RLS policies enforce everything** (see
  schema). Strong fit for the coach access rules.
- If a backend uses the service role → RLS is bypassed; **replicate the same
  checks in code**. The logic is identical; the schema's RLS section then serves
  as the spec for what the backend must enforce.

**Does the app even need a backend?** For a personal tracker, the Supabase SDK +
RLS can do auth, CRUD, and storage with no backend. A backend earns its place
for: secret third-party calls, heavy business logic, scheduled jobs, data
clients shouldn't touch directly, or a stable multi-client API contract. Worth
revisiting before committing to building one.

### 2.6 Storage

For workout photos/media: Supabase Storage offers an S3-compatible API plus a
REST API. From a backend, use the secret key to upload/download or generate
**signed URLs** the client uses directly.

---

## 3. Data model

### 3.1 Guiding principles (the "why")

- **Two parallel hierarchies: PLAN vs ACTUAL.** A prescribed set ("do 5 reps at
  100 kg") and a performed set ("did 5 reps at 100 kg") are different things with
  different lifecycles. Don't conflate them. The plan hierarchy mirrors the log
  hierarchy and they link via nullable FKs.
- **Plan mirrors actual 1-to-1.** `program_sets` has the same measurement
  columns as `sets`, so comparison/adherence is matching like-against-like and
  comes nearly for free. A "3×5" prescription expands to **3 individual
  `program_set` rows** so plan↔actual is a clean 1:1 link (a missed set = a
  `program_set` with no linked actual).
- **Weight and reps are peer columns** on a flat set row — never nest one under
  the other — so both directions of the history query are symmetric and
  trivially indexable.
- **Exercise catalog is a real referenced table**, not free-text per set
  (otherwise "Bench Press" vs "bench press" fragments history).
- **Denormalize `user_id`, `exercise_id`, `measurement_type` onto set rows.**
  Buys flat, indexed analytics and a self-contained CHECK, at the cost of small
  redundancy (these are derived from the parent/exercise and set by the app).
- **Measurement-type validation = Option B**: a `measurement_type` enum on the
  `exercises` catalog (classify each movement once), denormalized onto set rows,
  enforced by an identical `CHECK` on both `sets` and `program_sets`. Chosen over
  wide-nullable-no-validation (too loose) and JSONB (bad for the filtering/
  indexing that is the whole point).
- **Coaching is a relationship, not a column.** A `coach_athlete` join table
  supports many athletes per coach, switching coaches, invitations, and a coach
  who is also an athlete. Everyone is just an `auth.users` row; "coach"/"athlete"
  are roles defined by the relationship.
- **Programs separate `owner_id` from `athlete_id` from the start.** For
  self-coached users they're equal; this means the coaching layer adds policies,
  not a restructure.

### 3.2 Measurement types

An enum on `exercises` driving which set columns are required:

| type | required columns |
|------|------------------|
| `weight_reps` | weight + reps |
| `bodyweight` | reps |
| `weighted_bodyweight` | added_weight + reps |
| `duration` | duration_seconds |
| `distance_time` | distance_meters (+ optional duration_seconds for pace) |

### 3.3 Tables

**Catalog**
- `exercises` — id, name, category, `measurement_type`, `created_by` (NULL =
  global), created_at.

**Actual side (the log)**
- `workouts` — user_id, performed_at, notes, `program_workout_id` (nullable link
  to plan).
- `workout_exercises` — workout_id, exercise_id, position, notes.
- `sets` — workout_exercise_id, **user_id**, **exercise_id**,
  **measurement_type**, set_index, performed_at, measurement columns (weight,
  reps, added_weight, duration_seconds, distance_meters), **rpe (nullable)**,
  `program_set_id` (nullable link to plan), CHECK per measurement_type.

**Plan side (mirrors actual)**
- `programs` — owner_id, athlete_id, name, notes.
- `program_workouts` — program_id, week, day, name, position (positioned by
  week/day, not calendar date → reusable template).
- `program_exercises` — program_workout_id, exercise_id, position, notes.
- `program_sets` — program_exercise_id, exercise_id, measurement_type,
  set_index, same measurement columns, **rpe (nullable, "programmed RPE")**,
  same CHECK.

**Relationship**
- `coach_athlete` — coach_id, athlete_id, status (`pending`/`active`/`ended`),
  unique(coach_id, athlete_id), CHECK coach ≠ athlete.

### 3.4 Links between plan and actual

- `workouts.program_workout_id → program_workouts.id` (nullable)
- `sets.program_set_id → program_sets.id` (nullable)

Nullable so freestyle workouts work (FKs just NULL). When populated, adherence /
planned-vs-actual volume / auto-fill all become simple joins.

### 3.5 RPE

Nullable `rpe numeric(3,1)` on **both** set tables (range 0–10). Symmetric and
optional: a coach may prescribe it or leave it null; a user may log it or skip.

### 3.6 Indexes (for the headline queries)

On `sets`:
- `(user_id, exercise_id, weight)` — reps history at a given weight
- `(user_id, exercise_id, reps)` — weight history at a given rep count
- `(user_id, exercise_id, performed_at)` — history over time
- `(program_set_id)` — adherence joins

### 3.7 Open modeling decisions (flagged in schema comments)

- **`distance_time`:** is `duration_seconds` mandatory for runs, or optional
  (distance-only allowed)? Currently optional. Tighten that CHECK branch if time
  should be required.
- **`weighted_bodyweight` vs history queries:** decide whether `added_weight`
  participates in the weight/reps history views (e.g. "pull-up history at
  +20 kg"). If yes, add matching composite indexes on `added_weight`.
- **`measurement_type` integrity:** it's denormalized onto set rows; either trust
  the app to always derive it from `exercise_id`, or add a trigger to enforce it.

### 3.8 Units & precision (decide early — painful to retrofit)

- Store weight in **one canonical unit (kg)** as `numeric`, convert for display.
  Consider a `unit_preference` on the user. Mixed units silently corrupt "same
  weight" comparisons.
- Use **`numeric`/`decimal`, never float**, for weight/distance — plate math and
  equality comparisons get flaky with floating point.

---

## 4. Build roadmap (staged)

Design the full shape now; populate in stages. Each stage is shippable and
nothing gets rewritten later. Stages are tagged `[v1] / [v1.5] / [v2]` in
`schema.sql`.

### v1 — Logging only
- Tables: `exercises`, `workouts`, `workout_exercises`, `sets`.
- Include the nullable plan-link FK *columns* now (`program_workout_id`,
  `program_set_id`) even though nothing populates them — costs nothing as NULLs,
  saves a migration. (The FK *constraints* are added in v1.5 once the plan tables
  exist.)
- Measurement-type enum + CHECK.
- Indexes for the bidirectional history feature.
- RLS: own-data only (`user_id = auth.uid()`), or equivalent backend checks.
- **Deliverable:** complete, useful logging app with bidirectional weight/reps
  history.

### v1.5 — Self-authored programs
- Add `programs`, `program_workouts`, `program_exercises`, `program_sets`.
- Add the plan↔actual FK constraints.
- Web dashboard: build a program for yourself; link logged workouts to it.
- owner_id = athlete_id (no coach complexity yet); RLS stays at "own data".
- **Deliverable:** planned-vs-actual tracking, auto-fill logging from targets.

### v2 — Coaching
- Add `coach_athlete` + `is_active_coach_of()` helper.
- Add coach READ policies (separate, OR-ed onto v1 policies — purely additive).
- Programs can now have owner ≠ athlete.
- **Deliverable:** coaches author programs for athletes and view their logs.

---

## 5. Security / RLS notes

- RLS is enabled on all user-data tables in `schema.sql`.
- Coach cross-user access uses a `SECURITY DEFINER` helper `is_active_coach_of()`
  to keep policies readable and avoid recursive RLS evaluation. **Test this
  hardest** — a coach seeing the wrong athlete's data is a real privacy bug.
  Write a test proving coach A cannot see coach B's athletes.
- If using a **service-role backend** (RLS bypassed), the RLS section is the spec
  for the checks the backend must implement in code.

---

## 6. Example analytics queries

```sql
-- 1) Reps history at a given weight for one exercise
SELECT performed_at, reps FROM sets
WHERE user_id = auth.uid() AND exercise_id = $1 AND weight = $2
ORDER BY performed_at;

-- 2) Weight history at a given rep count for one exercise
SELECT performed_at, weight FROM sets
WHERE user_id = auth.uid() AND exercise_id = $1 AND reps = $2
ORDER BY performed_at;

-- 3) Adherence: planned vs performed (1-to-1 via program_set_id link)
SELECT ps.id AS planned, s.id AS actual,
       ps.weight AS tgt_w, s.weight AS act_w,
       ps.reps   AS tgt_r, s.reps   AS act_r
FROM program_sets ps
LEFT JOIN sets s ON s.program_set_id = ps.id
WHERE ps.program_exercise_id = $1;     -- missed sets => actual is NULL
```

---

## 7. Immediate next steps (for Claude Code)

1. **Validate `schema.sql`** against a real Postgres / throwaway Supabase project
   — it was authored but NOT run through a live parser. The queries in §6 are a
   good smoke test.
2. Create a Supabase project; **rotate to an asymmetric (ES256) signing key** and
   confirm it's issuing ES256 before wiring auth.
3. Seed a starter `exercises` catalog (global rows, `created_by` NULL) covering
   each `measurement_type`.
4. Decide SDK-direct-with-RLS vs backend-with-service-role (and whether a backend
   is needed at all for v1).
5. If building a backend, pick the stack (§2.4) and stand up JWKS verification
   first.
6. Build v1 logging end-to-end, including the bidirectional history queries +
   their indexes.

---

## 8. Decisions log (quick reference)

| Decision | Choice |
|----------|--------|
| Storage/auth provider | Supabase |
| JWT signing | Asymmetric (ES256), local JWKS verification |
| Backend stack | OPEN — .NET / Java-Kotlin / Go |
| Mobile framework | OPEN — Flutter / native (not a backend factor) |
| Program author UI | Web dashboard |
| Plan vs actual | Two parallel hierarchies, linked by nullable FKs |
| Plan ↔ actual fidelity | 1-to-1; "3×5" = 3 individual program_set rows |
| Exercise types | Option B: measurement_type enum + CHECK validation |
| measurement_type location | On `exercises`, denormalized onto set rows |
| RPE | Nullable on both set tables; programmable + trackable |
| Denormalized on set rows | user_id, exercise_id, measurement_type |
| Coaching | `coach_athlete` relationship table; roles not user types |
| Programs ownership | owner_id separate from athlete_id from the start |
| Weight storage | numeric (not float), single canonical unit (kg) |
| Access control | RLS (or equivalent backend checks if service-role) |
