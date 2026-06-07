---
paths:
  - "**/*Service.kt"
---

# Services & domain logic

The service layer is where business rules and **all authorization** live. It knows
nothing about HTTP (no `ResponseEntity`, no servlet types).

## Authorization is THIS layer's job (critical)
This is a service-role backend, so Postgres RLS is bypassed — the checks the RLS
policies in `schema.sql` describe must be enforced here, in code.

- Every read/write is scoped to the authenticated `userId`.
- Before writing/reading a nested resource, verify the **parent** belongs to the user
  (e.g. before adding a set, confirm the `workout_exercise` → `workout.user_id == userId`).
- A resource owned by someone else is `NotFound` or `Forbidden` — never silently acted on.
- The `userId` is always passed in from the controller (from the JWT). Never trust an
  id from the client payload.
- Write a test proving user A cannot touch user B's data (see `testing.md`).

## Set validation
- Derive `exercise_id` and `measurement_type` from the referenced exercise — not the
  client. Then validate the measurement column combo with
  `measurement/MeasurementValidator` before persisting. This mirrors the DB CHECK in
  `schema.sql`; the DB constraint is the backstop, the validator gives clear 400s.

## Transactions
- Wrap multi-statement writes in `@org.springframework.transaction.annotation.Transactional`
  at the service method. Reads generally don't need it.
- A workout + its exercises + sets created together must be one transaction.

## Errors & return types
- Throw domain exceptions (`NotFoundException`, `ForbiddenException`,
  `ValidationException` in `common/`) — the global handler maps them to HTTP.
- Return domain models or mapped results, never raw `JdbcClient` artifacts.
- Keep services free of SQL; call repository methods.
