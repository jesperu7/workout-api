# Workout Tracker — iOS App Plan & Backend Contract

Plan and handoff for the **native iOS client** of workout-api. Companion to the
backend docs: [`workout-tracker-plan.md`](workout-tracker-plan.md) (product + BE
architecture), [`ROADMAP.md`](ROADMAP.md) (BE status), [`README.md`](README.md)
(run the BE locally), [`schema.sql`](schema.sql) (data model).

> **Status (2026-06-13):** Backend is shipped through **v1.1** (logging, history,
> user-created exercises, guest accounts). This client doesn't exist yet — this doc
> is the starting plan. The live API contract is also browsable as **OpenAPI/Swagger**
> at `http://localhost:8080/swagger-ui.html` (raw spec: `/v3/api-docs`) when the BE runs.

---

## 1. Scope & approach

- **Platform:** native iOS (Swift / SwiftUI). Framework choice does **not** affect the
  backend.
- **What the client does:** day-to-day workout logging, browsing/searching the exercise
  catalog, creating custom exercises, and viewing bidirectional progress history.
- **What it is NOT (yet):** program authoring (BE v1.5) and coaching (BE v2) are future
  work, and will land first on a **web app for coaches/clients** — not this iOS app.
- **Auth split (already built this way):** the client uses the **Supabase Swift SDK**
  directly for all auth (sign-in, anonymous sign-in, token refresh). It talks to the
  **workout-api backend** for everything else, sending the Supabase access token as a
  `Bearer` header. The backend verifies the token's signature against Supabase's JWKS
  and enforces all ownership in code.
- **Data access is backend-only (enforced):** the data tables are **RLS-locked** so the
  Supabase SDK / anon key **cannot** read or write them directly via PostgREST. The SDK is
  for auth *only*; every piece of data goes through the API below. Don't plan any direct
  `supabase.from("…")` table calls — they'll return nothing.

```
┌─────────────────────────┐        Supabase Swift SDK (auth only)
│      iOS app (Swift)     │ ───────────────────────────────────────►  Supabase Auth (GoTrue)
│                          │        sign in / signInAnonymously / refresh
│  • Supabase session      │ ◄───────────────────────────────────────  returns JWT (+ refresh)
│  • API client (Bearer)   │
│                          │        HTTPS  Authorization: Bearer <jwt>
│                          │ ───────────────────────────────────────►  workout-api  (this repo)
└─────────────────────────┘ ◄───────────────────────────────────────  JSON (camelCase) / problem+json
                                     verifies JWT vs JWKS, scopes everything to sub
```

---

## 2. Auth & session (build this first)

The backend is a stateless OAuth2 resource server. Every `/api/**` call needs a valid
Supabase **ES256** JWT or it's `401`.

- **SDK:** [`supabase-swift`](https://github.com/supabase/supabase-swift) (or its `Auth`
  module). Configure with the Supabase URL + the **publishable/anon key**
  (`sb_publishable_…` — the same one in `scripts/dev-token.sh` for local).
- **Sign-in modes:**
  - **Guest:** `signInAnonymously()` → a real `auth.users` row with `is_anonymous: true`.
    Let users try the app with zero friction.
  - **Member:** email/password (`signUp` / `signInWithPassword`). Real account.
  - **Convert guest → member:** link an identity / sign up on the anonymous session —
    **the user id stays the same**, so everything they logged remains theirs. Build the
    "create an account to save your data" upgrade flow around this.
- **Token handling:**
  - Store the session in the **Keychain** (the SDK can persist it; never `UserDefaults`).
  - Supabase access tokens are **short-lived (~5 min)** — rely on the SDK's automatic
    refresh; always read the *current* access token right before a request.
  - On a `401` from the backend, force a refresh and retry once; if that fails, route to
    sign-in.
- **Role (drives UI gating):** read `is_anonymous` from the session's user. The backend
  maps it to `ROLE_GUEST` / `ROLE_MEMBER`; the **only** member-gated endpoint today is
  `POST /api/exercises`. Treat a missing claim as guest (same as the BE).

---

## 3. Backend API contract (complete, v1.1)

**Base URL:** `http://localhost:8080` (simulator/dev) → a cloud URL after deploy.
**Conventions:** JSON is **camelCase**; timestamps are **ISO-8601** (`OffsetDateTime`,
e.g. `2026-05-08T17:00:00Z`); all `/api/**` require `Authorization: Bearer <jwt>`.
**Decimals** (`weight`, `addedWeight`, `distanceMeters`, `rpe`) are JSON numbers backed by
SQL `numeric` — decode them into Swift **`Decimal`**, never `Double` (float drift breaks
plate math and equality; the backend is strict about this too).

### 3.1 Identity & infra

| Method | Path | Auth | Success | Notes |
|---|---|---|---|---|
| GET | `/actuator/health` | none (public) | `200 {"status":"UP"}` | liveness probe |
| GET | `/api/me` | any authenticated | `200 MeResponse` | echoes caller identity |

`MeResponse` → `{ "userId": "uuid", "email": "string|null" }` (email is null for guests).

### 3.2 Exercises (catalog: global + mine)

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| GET | `/api/exercises` | authenticated | `200 Page<ExerciseResponse>` | — |
| GET | `/api/exercises/{id}` | authenticated | `200 ExerciseResponse` | `404` if not global/mine |
| POST | `/api/exercises` | **MEMBER only** | `201 ExerciseResponse` | `403` guest · `400` invalid · `409` duplicate name |

**`GET /api/exercises` query params** (all optional): `name` (case-insensitive substring
search), `category` (exact), `measurementType` (enum, see §3.6), `limit` (default 50, max
200), `offset` (default 0). Returns global exercises **plus** the caller's own.

**`POST /api/exercises` body** (`CreateExerciseRequest`):
```json
{ "name": "Cable Crossover", "category": "push", "measurementType": "WEIGHT_REPS" }
```
`name` required, non-blank, ≤200 chars. `category` optional, ≤100. `measurementType`
required. Owner is taken from the JWT — never send a user id. Duplicate name **per user**
(case-insensitive) → `409`; reusing a *global* name is allowed (you shadow it).

`ExerciseResponse` → `{ "id", "name", "category"?, "measurementType", "global": bool }`
(`global: true` = built-in catalog row; `false` = created by the caller).

### 3.3 Exercise history (the headline feature)

| Method | Path | Auth | Success |
|---|---|---|---|
| GET | `/api/exercises/{exerciseId}/history` | authenticated | `200 SetHistoryPoint[]` |

Query params decide the direction (mutually useful, scoped to the caller's own sets):
- `?weight=100` → **reps achieved at that weight**, over time.
- `?reps=5` → **weight used at that rep count**, over time.
- *(neither)* → **all sets for the exercise**, over time.

`404` if the exercise isn't visible to you. Returns a **plain array** (not paged),
ordered by `performedAt`:
```json
[ { "performedAt": "2026-05-01T17:00:00Z", "weight": 100, "reps": 5,
    "addedWeight": null, "durationSeconds": null, "distanceMeters": null, "rpe": 8 } ]
```
Which fields are meaningful depends on the exercise's measurement type (§3.6).

### 3.4 Workouts

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| POST | `/api/workouts` | authenticated | `201 WorkoutResponse` | `400` |
| GET | `/api/workouts` | authenticated | `200 Page<WorkoutResponse>` | — |
| GET | `/api/workouts/{id}` | authenticated | `200 WorkoutResponse` | `404` if not yours |
| PATCH | `/api/workouts/{id}` | authenticated | `200 WorkoutResponse` | `404` · `400` |
| DELETE | `/api/workouts/{id}` | authenticated | `204` | `404` |

`GET /api/workouts` takes `limit` / `offset`. **Create** body: `{ "performedAt"?: iso,
"notes"?: "≤2000 chars" }` — `performedAt` defaults to now. **PATCH** applies only the
non-null fields you send (v1 can't clear `notes` back to null). **DELETE** cascades to the
workout's exercises and sets.

`WorkoutResponse` → `{ "id", "performedAt", "notes"?, "programWorkoutId"?, "createdAt" }`
(`programWorkoutId` is always null until BE v1.5 — keep the field, ignore it for now).

### 3.5 Workout exercises & sets (the logging chain)

A logged workout is a tree: **workout → workout-exercises → sets**.

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| POST | `/api/workouts/{workoutId}/exercises` | authenticated | `201 WorkoutExerciseResponse` | `404` workout/exercise · `400` |
| GET | `/api/workouts/{workoutId}/exercises` | authenticated | `200 WorkoutExerciseResponse[]` | `404` |
| PATCH | `/api/workout-exercises/{id}` | authenticated | `200 WorkoutExerciseResponse` | `404` · `400` |
| DELETE | `/api/workout-exercises/{id}` | authenticated | `204` | `404` |
| POST | `/api/workout-exercises/{workoutExerciseId}/sets` | authenticated | `201 SetResponse` | `404` · `400` bad combo |
| GET | `/api/workout-exercises/{workoutExerciseId}/sets` | authenticated | `200 SetResponse[]` | `404` |
| PATCH | `/api/sets/{id}` | authenticated | `200 SetResponse` | `404` · `400` |
| DELETE | `/api/sets/{id}` | authenticated | `204` | `404` |

Nested list endpoints (`…/exercises`, `…/sets`) return **plain arrays**, not page
envelopes.

**Add-exercise body** (`AddWorkoutExerciseRequest`): `{ "exerciseId": "uuid",
"position"?: int≥0, "notes"?: "≤2000" }`. The exercise must be visible to you (global or
yours) or it's `404`.
`WorkoutExerciseResponse` → `{ "id", "workoutId", "exerciseId", "position", "notes"? }`.

**Log-set body** (`CreateSetRequest`) — send only the fields the exercise's measurement
type requires (§3.6); the server derives `measurementType` from the exercise and rejects a
wrong combo with `400`:
```json
{ "setIndex"?: int≥0, "performedAt"?: iso,
  "weight"?: ≥0, "reps"?: int≥0, "addedWeight"?: ≥0,
  "durationSeconds"?: int≥0, "distanceMeters"?: ≥0, "rpe"?: 0..10 }
```
`SetResponse` echoes everything incl. the derived `measurementType`:
`{ "id", "workoutExerciseId", "exerciseId", "measurementType", "setIndex",
"performedAt", "weight"?, "reps"?, "addedWeight"?, "durationSeconds"?,
"distanceMeters"?, "rpe"? }`. PATCH merges with the existing set and re-validates.

### 3.6 Measurement types — the rule that drives the set-logging UI

`measurementType` is an enum; **wire values are UPPERCASE** (`WEIGHT_REPS`, `BODYWEIGHT`,
`WEIGHTED_BODYWEIGHT`, `DURATION`, `DISTANCE_TIME`). It's fixed per exercise and decides
**which input fields the client must show and send**. Mirror this matrix exactly — the
backend enforces it, so showing the wrong fields produces `400`s:

| `measurementType` | Required fields | Also allowed | Forbidden (don't send) |
|---|---|---|---|
| `WEIGHT_REPS` | `weight`, `reps` | — | addedWeight, durationSeconds, distanceMeters |
| `BODYWEIGHT` | `reps` | — | weight, addedWeight, durationSeconds, distanceMeters |
| `WEIGHTED_BODYWEIGHT` | `addedWeight`, `reps` | — | weight, durationSeconds, distanceMeters |
| `DURATION` | `durationSeconds` | — | weight, reps, addedWeight, distanceMeters |
| `DISTANCE_TIME` | `distanceMeters` | `durationSeconds` (pace) | weight, reps, addedWeight |

`rpe` (0–10, one decimal) is optional on **every** type. `weight`/`addedWeight` are in
**kilograms** (canonical); do unit conversion (kg↔lb) for display only.

### 3.7 Error contract (RFC 7807 `application/problem+json`)

Every error shares one shape — decode it into a typed `APIError` and surface `detail`:
```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "you already have an exercise named \"Sled Push\"" }
```
| Status | When | Client handling |
|---|---|---|
| `400` | bean validation or a bad measurement combo | show `detail`; fix the form |
| `401` | missing/expired/invalid token | refresh once, else sign-in |
| `403` | a **guest** hit a member-only route (`POST /api/exercises`) | prompt to create an account |
| `404` | not found **or not yours** (ownership is hidden as 404) | treat as "doesn't exist" |
| `409` | duplicate exercise name (per user) | suggest searching the catalog first |

> Note the deliberate split: **ownership** failures return `404` (you can't tell someone
> else's resource exists), but the **guest gate** returns `403`. Don't conflate them.

---

## 4. What the client must implement

1. **Session layer** — Supabase SDK wiring; anonymous + email auth; Keychain persistence;
   auto-refresh; expose "current access token" + "is guest".
2. **API client** — one networking type that injects the Bearer token, encodes/decodes
   camelCase JSON with ISO-8601 dates and `Decimal` numerics, decodes `problem+json` into
   a typed error, and does the 401-refresh-retry. Base URL from build config.
3. **Models** — `Codable` structs for every response DTO above, the `MeasurementType` enum
   (uppercase raw values), and a generic `Page<T>` (`items`, `limit`, `offset`, `total`).
4. **Measurement-driven set forms** — one form that renders the right inputs from the
   exercise's `measurementType` (§3.6). This is the single most important piece of client
   logic; it mirrors the server validator.
5. **Role gating** — hide/disable "Create exercise" for guests; still handle a `403`
   defensively. A persistent "Save your progress — create an account" prompt for guests.
6. **Unit handling** — store/send kg; let the user view kg or lb.

### Screen → endpoint map

| Screen | Endpoints |
|---|---|
| Launch / onboarding | SDK `signInAnonymously` or `signInWithPassword`; `GET /api/me` |
| Exercise catalog (search/filter) | `GET /api/exercises?name=&category=&measurementType=` |
| Exercise detail + history charts | `GET /api/exercises/{id}` · `GET /api/exercises/{id}/history[?weight=|?reps=]` |
| Create exercise (member) | `POST /api/exercises` |
| Workout list | `GET /api/workouts` |
| Active workout (log session) | `POST /api/workouts` → `POST /api/workouts/{id}/exercises` → `POST /api/workout-exercises/{id}/sets` |
| Edit / delete in a workout | `PATCH`/`DELETE` on `/workouts`, `/workout-exercises`, `/sets` |
| Account upgrade (guest→member) | SDK identity-link / sign-up (same user id) |

---

## 5. Client build roadmap (phased)

Each phase is independently demoable in the simulator against the local backend.

- **C0 — Foundations:** Xcode project, Supabase SDK, API client + models, anonymous sign-in,
  `GET /api/me` smoke test against `localhost:8080`.
- **C1 — Catalog:** browse + name-search + filter exercises; exercise detail.
- **C2 — Logging:** create a workout, add exercises, log/edit/delete sets with
  measurement-aware forms. (The core loop.)
- **C3 — History:** per-exercise charts in both directions (reps@weight, weight@reps) and
  over time.
- **C4 — Custom exercises + accounts:** member-gated create; guest→member upgrade flow;
  guest prompts.
- **C5 — Polish & device:** empty/error/loading states, units, offline-friendliness; then
  the backend **cloud deploy** task → switch base URL → test on a real device.

> Maps onto backend **v1 + v1.1** (already shipped). Program features (BE v1.5) and coaching
> (BE v2) come later and target the **web app**, not this client.

---

## 6. Dev setup & environments

- **Run the backend locally:** `supabase start` then `./gradlew bootRun` (see README).
- **Simulator → localhost works:** the iOS simulator shares the Mac's network, so
  `http://localhost:8080` (API) and `http://127.0.0.1:54321` (Supabase) are reachable
  directly. Start here.
- **Real device needs a reachable backend:** a phone can't see your Mac's `localhost`.
  Options: your Mac's LAN IP on the same Wi-Fi (quick hack), or — the planned path — the
  **"deploy to cloud" task** (cloud Supabase project + deployed API image) before
  device testing. Point the client's base URL at the deployed host then.
- **App Transport Security:** ATS blocks plain HTTP by default, so for local dev add an
  **`NSAllowsLocalNetworking`** exception in Info.plist (Debug only) to allow
  `http://localhost`. The deployed backend will be **HTTPS**, which needs no exception.
- **Config:** keep base URLs + the Supabase publishable key in build configs
  (Debug=local, Release=cloud), not hardcoded.

---

## 7. Open client decisions

- SwiftUI vs UIKit (lean SwiftUI for a greenfield app).
- Charting lib for history (Swift Charts is the obvious default).
- Local cache / offline strategy — fine to defer; the API is the source of truth for v1.
- Minimum iOS version (affects Swift Charts / SDK availability).
