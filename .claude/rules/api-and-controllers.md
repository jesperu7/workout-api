---
paths:
  - "**/*Controller.kt"
  - "**/dto/**"
  - "**/web/**"
---

# API & controllers

## Controllers are thin
- `@RestController`, base path under `/api`. Resource-oriented, plural nouns
  (`/api/workouts`, `/api/exercises`).
- A controller method: validate input → get the user → call **one** service method →
  map result to a response DTO → return. No business logic, no `JdbcClient`, no SQL.
- Get the acting user from the authenticated principal (M2 adds a `CurrentUser` helper).
  **Never** read a user id from the request body/query/path to identify the caller.

## DTOs
- Separate request and response DTOs per endpoint; never accept or return DB-shaped
  models directly. Map in the controller (or a small mapper) — keeps the wire contract
  stable as the schema evolves.
- Validate request DTOs with `jakarta.validation` annotations + `@Valid`:
  `@field:NotNull`, `@field:Positive`, `@field:Size`, etc. (Kotlin needs the
  `@field:` target.)
- JSON is **camelCase** (Jackson default); the DB is snake_case — map at the repository.
- Timestamps are ISO-8601.

## Status codes
- `200` OK, `201` Created (+ `Location` when sensible), `204` No Content (delete),
  `400` validation, `401` unauthenticated, `403` ownership/forbidden, `404` not found,
  `409` conflict (e.g. duplicate global exercise name).

## Errors
- Do **not** try/catch and hand-format errors in controllers. Throw domain exceptions
  in the service; a single `@RestControllerAdvice` in `common/` renders RFC 7807
  `application/problem+json`. Keep one error shape across the API.

## Lists & pagination
- List endpoints accept `limit` (default 50, max 200) and `offset` (default 0), passed
  through to the repository as bound parameters. Document defaults in the DTO/handler.
