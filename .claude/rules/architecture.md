---
paths:
  - "**/*.kt"
---

# Architecture

Layered, **package-by-feature**. One-way dependency, never reversed:

```
HTTP → Controller → Service → Repository → JdbcClient → Postgres
```

A lower layer never imports a higher one. Controllers don't call repositories
directly; services don't import HTTP/servlet types.

## Layer responsibilities

| Layer | Does | Never does |
|---|---|---|
| **Controller** (`*Controller`) | Maps HTTP ↔ DTOs, validates input (`@Valid`), reads the authenticated user, calls one service method, sets the status code | business logic, SQL, ownership checks |
| **Service** (`*Service`) | Business rules, **ownership/authorization**, measurement validation, transaction boundaries, orchestration across repositories | touch HTTP types, write SQL |
| **Repository** (`*Repository`) | `JdbcClient` pure SQL, row→model mapping | business logic, authorization |
| **Model / DTO** | `data class`es: domain models (DB-shaped) and API DTOs (request/response), kept separate | — |

## Package layout (by feature)

```
com.workout.api
├─ WorkoutApiApplication.kt
├─ config/        @Configuration beans (SecurityConfig in M2)
├─ common/        GlobalExceptionHandler, ApiError, domain exceptions, paging types
├─ auth/          JWT principal → userId helper (M2)
├─ measurement/   MeasurementType enum (mirrors the DB enum) + MeasurementValidator
├─ exercise/      ExerciseController, ExerciseService, ExerciseRepository, Exercise, dto/
├─ workout/       Workout + WorkoutExercise + Set (one aggregate): controllers/services/repos/models/dto
└─ history/       read-only bidirectional-history queries over `sets`
```

A feature package owns its full vertical slice. Adding `program/` (v1.5) or
`coaching/` (v2) means a new package, not edits across shared layer folders.

## Where does X go?

- New endpoint → a `*Controller` method in the feature package + a `*Service` method.
- New business rule / ownership check → the `*Service`.
- New query → a `*Repository` method (SQL only).
- Shared cross-feature logic → `common/` (or `measurement/` for set-shape rules).
- Shared validation of set columns → `measurement/MeasurementValidator` (used by `sets`
  now and `program_sets` in v1.5 — write it reusable).

## Request lifecycle example — log a set

`POST /api/workout-exercises/{id}/sets`
1. `SetController`: validate the request DTO, get `userId` from the JWT principal.
2. `SetService`:
   - load the `workout_exercise` and verify it belongs to `userId` (else `Forbidden`/`NotFound`),
   - load its `exercise` to derive `exercise_id` + `measurement_type`,
   - `MeasurementValidator` checks the column combo for that type,
   - `SetRepository.insert(...)` within a transaction.
3. `SetController`: map the created row to a `SetResponse`, return `201`.

## Deliberately NOT used

No JPA/Hibernate, no hexagonal/ports-and-adapters, no DDD aggregates framework, no
CQRS, no microservices. This is a single, conventional layered Spring service. Keep it
that way unless there's a concrete, discussed reason to change.
