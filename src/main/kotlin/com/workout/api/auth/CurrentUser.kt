package com.workout.api.auth

import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

/**
 * The authenticated user's id, taken from the Supabase JWT `sub` claim.
 *
 * This is the ONLY source of the acting user — never trust a user id from the request body,
 * query, or path. Controllers read it from `@AuthenticationPrincipal Jwt` and pass it down.
 */
val Jwt.userId: UUID
    get() = UUID.fromString(subject)
