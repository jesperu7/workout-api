# workout-api

Backend API for a workout-tracking app. Kotlin + Spring Boot, talking to a
Supabase Postgres. See [`workout-tracker-plan.md`](workout-tracker-plan.md) for
the product/architecture plan and [`schema.sql`](schema.sql) for the full
staged data model.

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

**Authorization model:** this is a service-role backend, so Supabase RLS is
*bypassed*. Ownership/coach rules are enforced in Kotlin; the RLS section of
`schema.sql` is the spec for those checks.

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
```

Run this after changing any `spring.security.oauth2.resourceserver.*` config — it's the
interim guard for the auth decoder until the WireMock ES256 test lands (M6). The same calls
are in `requests.http` for the IDE REST client.

## Migrations

- `V1__init_v1_logging.sql` — measurement-type enum, `exercises` catalog, and the
  actual-side log (`workouts`, `workout_exercises`, `sets`) + history indexes.
- `V2__seed_global_exercises.sql` — starter global catalog covering each type.

The `[v1.5]` program tables and `[v2]` coaching tables (and the plan↔actual FK
constraints) land as later additive migrations. RLS is intentionally deferred
(see the comment header in `V1`).

## Roadmap

`M0` scaffold ✅ · `M1` local DB + connectivity ✅ · `M2` Supabase JWT auth ✅ ·
`M3` exercise catalog ← next · `M4` logging CRUD · `M5` bidirectional history ·
`M6` hardening. Full detail in `workout-tracker-plan.md`.
