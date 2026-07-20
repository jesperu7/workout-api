# workout-api

Backend API for a workout-tracking app. Kotlin + Spring Boot, talking to a
Supabase Postgres. See [`ROADMAP.md`](ROADMAP.md) for status & remaining work,
[`workout-tracker-plan.md`](workout-tracker-plan.md) for the product/architecture plan,
[`ios-app-plan.md`](ios-app-plan.md) for the native iOS client plan + full API contract,
[`planning-notes.md`](planning-notes.md) for forward-looking decisions (hosting, offline-first, guests), and
[`schema.sql`](schema.sql) for the staged data model.

## Stack

| Concern | Choice |
|---|---|
| Language / runtime | Kotlin, JDK 17 |
| Framework | Spring Boot 4.0 (`spring-boot-starter-webmvc`) |
| DB access | Plain SQL via Spring `JdbcClient` (`spring-boot-starter-jdbc`) |
| Migrations | Flyway (`src/main/resources/db/migration`) |
| Database | Postgres (Supabase) |
| Auth | Supabase ES256 JWT, verified locally via JWKS (oauth2 resource server) |
| Tests | JUnit 5 + Testcontainers (Postgres) |
| Build | Gradle (Kotlin DSL), wrapper included |

**Authorization model:** this is a service-role backend, so it *bypasses* Supabase
RLS and enforces ownership/coach rules in Kotlin (the RLS section of `schema.sql` is
the spec for those checks). Clients are **RLS-locked out** of the data tables (`V4`:
RLS enabled, no policies), so the public anon key can't bypass the API via PostgREST —
all data goes through this backend; clients use Supabase for auth only. Each token also
maps to one role from the `is_anonymous` claim — `ROLE_MEMBER` (real account) or
`ROLE_GUEST` (anonymous sign-in); guests can browse and log but not create catalog content.

## Prerequisites

- JDK 17 (`java -version`)
- [Docker](https://docs.docker.com/get-docker/)-compatible runtime (for the
  Supabase local stack and Testcontainers) — e.g. colima / OrbStack / Docker Desktop
- [Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started)

## Run locally (needs Docker)

`supabase/config.toml` is committed, so a fresh clone runs the full DB with:

```bash
supabase start         # boots Postgres (54322) + Auth/JWKS (54321) in Docker
./gradlew bootRun      # Flyway applies V1/V2 (schema + seed) against the local DB
```

> `supabase init` was already run when the repo was created — don't re-run it on a clone.
> Postgres **data** lives in Docker volumes (not git); the schema + seed are reproduced
> from the Flyway migrations the moment the app runs (or `./gradlew test`).

Default datasource points at the local Supabase Postgres
(`jdbc:postgresql://127.0.0.1:54322/postgres`, `postgres`/`postgres`); override
with `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD`.

```bash
./gradlew test         # integration tests spin up their own Postgres container
./gradlew build        # full build + tests
```

> Without Docker you can still compile: `./gradlew compileKotlin compileTestKotlin`.

## Docker

```bash
docker build -t workout-api:local .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:postgresql://host.docker.internal:54322/postgres' \
  -e SUPABASE_JWKS_URI='http://host.docker.internal:54321/auth/v1/.well-known/jwks.json' \
  workout-api:local
```

> Inside a container `localhost` is the container itself, so reach the host's Supabase stack
> via `host.docker.internal` (DB 54322, JWKS 54321). The multi-stage build runs `bootJar`
> (no tests — run `./gradlew check` on the host).

## Manual auth check

Verify the JWT auth path end-to-end against the local stack (two terminals; needs
`supabase start` running):

```bash
# terminal 1 — run the app
./gradlew bootRun

# terminal 2 — mint a real token and call the API
curl -s localhost:8080/actuator/health                                  # public  -> {"status":"UP"}
TOKEN=$(scripts/dev-token.sh)                                           # ES256 token for dev@workout.test
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/me         # -> 200 {"userId":...,"email":...}
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/me          # no token -> 401

# guest (anonymous) account — can log, but POST /api/exercises -> 403
GUEST=$(scripts/dev-token.sh --guest)                                   # mints a fresh anonymous user
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/exercises \
  -H "Authorization: Bearer $GUEST" -H 'Content-Type: application/json' \
  -d '{"name":"Guest Move","measurementType":"BODYWEIGHT"}'             # members only -> 403
```

Handy after changing any `spring.security.oauth2.resourceserver.*` config (the automated
guard is `JwtDecoderTest`). The same calls are in `requests.http` for the IDE REST client,
and `scripts/seed-demo-data.sh` logs a few sets so the history endpoints have data.

## API docs (Swagger UI)

With the app running (see above), springdoc serves the OpenAPI docs:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Both are public — browsing needs no token (the automated guard is `OpenApiDocsTest`).
To execute requests from the UI, click **Authorize** and paste a token
(`scripts/dev-token.sh` mints one for the local stack).

> The spec is generated from the controllers at runtime, so it documents whatever code
> the running app was built from. If endpoints look stale, the JVM on 8080 predates your
> changes — `pkill -f com.workout.api.WorkoutApiApplication` clears a stray instance.

## API

All routes need a Supabase Bearer token except `GET /actuator/health`. JSON is camelCase;
errors are RFC 7807 `application/problem+json`. Everything user-owned is scoped to the JWT
`sub` — another user's resource returns `404`. The two list endpoints (`/api/exercises`,
`/api/workouts`) return a page envelope `{ items, total, limit, offset }`; nested lists are plain arrays.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/me` | the authenticated user (id + email) |
| GET | `/api/exercises` `/{id}` | catalog: global + my exercises (`?name=` search, `?category=`, `?measurementType=`, `?limit=`, `?offset=`) |
| POST | `/api/exercises` | create my own exercise (members only — guests get `403`; duplicate name per user → `409`) |
| GET | `/api/exercises/{id}/history` | `?weight=` reps@weight · `?reps=` weight@reps · neither = over time |
| POST GET PATCH DELETE | `/api/workouts` (`/{id}`) | workout CRUD; optional `name` ("Push Day"); list takes `?name=` (exact match) for "repeat last workout" |
| POST GET | `/api/workouts/{id}/exercises` | add / list exercises in a workout |
| PATCH DELETE | `/api/workout-exercises/{id}` | update / remove a workout-exercise |
| POST GET | `/api/workout-exercises/{id}/sets` | log / list sets (validated per measurement type) |
| PATCH DELETE | `/api/sets/{id}` | update / delete a set |

## Migrations

- `V1__init_v1_logging.sql` — measurement-type enum, `exercises` catalog, and the
  actual-side log (`workouts`, `workout_exercises`, `sets`) + history indexes.
- `V2__seed_global_exercises.sql` — starter global catalog covering each type.
- `V3__user_exercise_unique_name.sql` — per-user unique exercise names (user-created
  exercises, v1.1).
- `V4__rls_lockout.sql` — enable RLS (no policies) on the data tables so clients can't
  reach Postgres directly via PostgREST; all access goes through this backend.
- `V5__add_workout_name.sql` — optional `name` column on `workouts` (named sessions).

The `[v1.5]` program tables and `[v2]` coaching tables (and the plan↔actual FK
constraints) land as later additive migrations. RLS is intentionally deferred
(see the comment header in `V1`).

## Status

v1 is shipped; see [`ROADMAP.md`](ROADMAP.md) for what's done and what's next.
