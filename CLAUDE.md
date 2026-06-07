# workout-api — project guide for Claude

Backend API for a workout tracker. **Kotlin + Spring Boot 4**, **plain SQL via
`JdbcClient`**, **Flyway** migrations, **Supabase** Postgres + Auth.

Design docs (read when relevant): [`workout-tracker-plan.md`](workout-tracker-plan.md)
(product + architecture), [`schema.sql`](schema.sql) (full staged data model),
[`README.md`](README.md) (run instructions).

> The maintainer is newer to Kotlin/backend and wants to write **pure SQL**.
> Favor clear, conventional code over clever abstractions, and explain non-obvious
> choices briefly in comments or the response.

## Stack & commands

- Kotlin, JDK 17, Gradle (Kotlin DSL, wrapper committed). Spring Boot 4.0.6.
- DB access: Spring `JdbcClient` (raw SQL). No JPA/Hibernate, no ORM.
- Migrations: Flyway, `src/main/resources/db/migration`.
- Tests: JUnit 5 + Testcontainers (Postgres).

```bash
./gradlew compileKotlin compileTestKotlin   # compile (no Docker needed)
./gradlew test                              # tests (needs Docker: Testcontainers)
./gradlew bootRun                           # run against local Supabase DB
supabase start                              # local Postgres :54322, Auth/JWKS :54321
```

## Architecture (detail in `.claude/rules/architecture.md`)

Layered, **package-by-feature**. Strict one-way dependency:
**Controller → Service → Repository → DB**. Never the reverse.

- **Controller** (`*Controller`): HTTP only — validate request DTOs, get the
  authenticated user, call a service, return a response DTO. No logic, no SQL.
- **Service** (`*Service`): business rules, ownership checks, validation, transactions.
- **Repository** (`*Repository`): `JdbcClient` pure SQL; returns models.
- **Model/DTO**: `data class`es; API DTOs are separate from DB-shaped models.

Packages: `config/ common/ auth/ measurement/ exercise/ workout/ history/`.

## Mandatory workflow (before writing code)

1. **Identify** the task type (controller / service / repository / migration / test).
2. **Read** the matching `.claude/rules/*.md` guide (they auto-load by path, but read
   explicitly when creating the first file of a kind).
3. **Grep** for 2–3 existing examples of the same kind and match their shape.
4. **Check** against the zero-tolerance rules below.
5. **Implement** following the patterns.
6. **Verify**: run the relevant `./gradlew` task. State results honestly
   (✅ verified vs. ⚠️ assumption). Always run tests after editing them.

## Zero-tolerance rules (always apply)

1. **Scope every query to the authenticated user.** This is a service-role backend —
   Supabase RLS is bypassed, so ownership is enforced *here, in code*. The RLS section
   of `schema.sql` is the spec. Verify parent ownership before nested writes.
2. **Never trust a client-supplied user id.** The acting user comes only from the
   validated JWT (`sub`), never from a request body/param/path.
3. **SQL uses named parameters only** (`:name`). Never string-concatenate values into
   SQL — that's an injection hole.
4. **SQL lives only in repositories.** Controllers and services never touch `JdbcClient`.
5. **Never expose DB-shaped models as API responses.** Map to a DTO.
6. **Weight/distance are `BigDecimal`** (DB `numeric`), never `Double`/float. Weight is
   stored in kilograms (canonical unit).
7. **Derive `measurement_type` from the exercise** and validate the column combo before
   persisting a set (mirror the DB CHECK). Never take it from the client.
8. **Applied migrations are immutable.** Never edit `V1`/`V2`; add a new `V3+`. The
   `auth.users` shim in `src/test/resources` is test-only.
9. **No `!!`** (not-null assertion). Handle nulls explicitly.

## Rules index (`.claude/rules/`, auto-load by path)

- `architecture.md` — layers, package layout, request lifecycle (`**/*.kt`)
- `kotlin-style.md` — Kotlin conventions for this project (`**/*.kt`)
- `api-and-controllers.md` — REST, DTOs, validation, errors (`*Controller.kt`, `dto/`)
- `services-and-domain.md` — business logic, ownership, transactions (`*Service.kt`)
- `database-and-sql.md` — JdbcClient SQL, mapping, Flyway (`*Repository.kt`, `*.sql`)
- `testing.md` — Testcontainers, mandatory authorization tests (`*Test.kt`, `test/`)

## Roadmap

`M0` scaffold ✅ · `M1` local DB + connectivity · `M2` Supabase JWT auth ·
`M3` exercise catalog · `M4` logging CRUD · `M5` bidirectional history · `M6` hardening.
Build only the current milestone's scope; the schema is staged `[v1]`/`[v1.5]`/`[v2]`.
```
