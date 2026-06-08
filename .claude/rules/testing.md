---
paths:
  - "**/*Test.kt"
  - "**/*Tests.kt"
  - "**/src/test/**"
---

# Testing

Tests run against **real Postgres via Testcontainers** — never mock the database.
Always run tests after writing or editing them (`./gradlew test`, needs Docker).

## Integration tests (the default)
- `@SpringBootTest` + `@Import(TestcontainersConfiguration::class)` boots the app
  against a throwaway Postgres container; Flyway applies the real migrations (plus the
  test `auth.users` shim).
- Test controllers through the HTTP layer (MockMvc / `WebTestClient`) and repositories
  through their public methods. Assert on real DB state.
- Seed users by inserting fixed UUIDs into `auth.users` (the shim table), then use those
  ids as the acting user.

## Mandatory: authorization tests
For every user-data endpoint, prove isolation:
- user A creates a resource; user B gets `403`/`404` on read, update, and delete;
- list endpoints return only the caller's rows.
This is the highest-value test in the project — ownership is enforced in code, so it
must be tested in code. (See `schema.sql` §5: "test this hardest".)

## Unit tests (pure logic, no Spring)
- `MeasurementValidator`: one test per `measurement_type`, asserting valid combos pass
  and every invalid column combo is rejected.
- Any other pure function (mappers, calculations).

## Real JWT decoder coverage
`JwtDecoderTest` exercises the actual `JwtDecoder` end-to-end: a tiny in-JVM HTTP server
publishes a test EC key as JWKS, tokens are signed with the matching private key, and it
asserts a valid ES256 token is accepted while tampered / wrong-audience / expired tokens are
rejected. The `jwt()`-mock tests bypass the decoder, so this is the one that catches an
algorithm/audience misconfiguration (e.g. the M2 ES256-vs-RS256 bug). `scripts/dev-token.sh`
stays as the quick manual check against the live stack.

## Conventions
- Descriptive names with backticks:
  ``fun `logging a set on someone else's workout is forbidden`() { ... }``.
- Arrange / act / assert; one behavior per test.
- Cover the headline history queries with seeded data proving both directions
  (reps@weight and weight@reps).
