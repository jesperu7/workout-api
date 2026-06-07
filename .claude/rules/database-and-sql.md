---
paths:
  - "**/*Repository.kt"
  - "**/*.sql"
  - "**/db/migration/**"
---

# Database & SQL

Data access is **plain SQL via Spring `JdbcClient`** (injected). No ORM, no Spring Data
repositories. Repositories contain SQL and row mapping only — no business logic, no
authorization (the service already scoped the call).

## JdbcClient patterns
- **Named parameters only.** Never concatenate or interpolate values into SQL —
  that is a SQL-injection hole and is non-negotiable.
  ```kotlin
  fun findById(id: UUID, userId: UUID): Workout? =
      jdbc.sql("""
          SELECT id, user_id, performed_at, notes
          FROM workouts
          WHERE id = :id AND user_id = :userId
      """)
        .param("id", id)
        .param("userId", userId)
        .query(Workout::class.java)   // or a RowMapper
        .optional()
        .orElse(null)
  ```
- Map rows explicitly to `data class`es (constructor-name mapping or a `RowMapper`).
  Keep DB snake_case → map to camelCase model/DTO fields.
- `numeric` columns → `BigDecimal`. Read/write with `BigDecimal`, never `Double`.
- Return models or nullable/lists; let the service decide what "missing" means.
- Even though the service scopes ownership, repository queries that touch user data
  should still take and filter by `userId` (defense in depth).

## Flyway migrations (`src/main/resources/db/migration`)
- **Applied migrations are immutable.** Never edit `V1`/`V2` once they've run; fix
  forward with a new `V3__describe_change.sql`. Versions are sequential `V<n>__<snake>.sql`.
- Transcribe DDL faithfully from `schema.sql`; that file is the design source of truth.
  Build only the current milestone's stage (`[v1]` now; `[v1.5]`/`[v2]` later, additive).
- The `[v1.5]` plan↔actual FK *constraints* come as their own migration when the plan
  tables land; the nullable link *columns* already exist in `V1`.
- `auth.users` FKs require Supabase's `auth` schema. Real/local-Supabase envs have it;
  tests get a minimal stand-in from `src/test/resources/db/migration/beforeMigrate.sql`
  — that shim is **test-only**, never add it to `src/main`.
- Don't drop the history indexes on `sets` (`sets_user_exercise_weight/reps/time`); the
  bidirectional-history queries depend on them.
