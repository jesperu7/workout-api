---
paths:
  - "**/*.kt"
---

# Kotlin style (this project)

Written for a maintainer newer to Kotlin — prefer clear and conventional.

## Immutability & null-safety
- Prefer `val` over `var`. Models and DTOs are immutable `data class`es.
- **No `!!`** (not-null assertion). Handle absence explicitly:
  - `value ?: throw NotFoundException("workout $id")`
  - `requireNotNull(x) { "x must be set" }` for programmer errors.
- A nullable DB column maps to a nullable Kotlin type (`Int?`, `BigDecimal?`).

## Types that map to the schema
- `uuid` ↔ `java.util.UUID`
- `timestamptz` ↔ `java.time.OffsetDateTime` (or `Instant`); never `Date`.
- `numeric` (weight, added_weight, distance, rpe) ↔ `java.math.BigDecimal`. **Never**
  `Double`/`Float` for these — equality and plate math break with floats.
- `int` ↔ `Int`; `text` ↔ `String`.
- `measurement_type` enum ↔ a Kotlin `enum class MeasurementType` mirroring it exactly.

## Dependency injection
- **Constructor injection only**, via `val` parameters:
  ```kotlin
  @Service
  class WorkoutService(private val workouts: WorkoutRepository) { ... }
  ```
- No `@Autowired` field injection. No `lateinit` for dependencies.

## Naming & shape
- `XxxController`, `XxxService`, `XxxRepository`; DTOs `XxxRequest` / `XxxResponse`.
- Public functions: explicit return types. Keep functions small and single-purpose.
- Expression bodies when they stay readable: `fun foo() = ...`.
- No wildcard imports. 4-space indentation (matches the generated project).
- Package names lowercase: `com.workout.api.workout`.

## Misc
- Use `data class` `copy()` for derived instances rather than mutating.
- Prefer standard library (`map`, `filter`, `firstOrNull`) over manual loops.
- Don't catch exceptions just to rethrow; let domain exceptions reach the
  `GlobalExceptionHandler` (see `common/`).
