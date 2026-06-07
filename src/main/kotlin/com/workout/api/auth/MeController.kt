package com.workout.api.auth

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Trivial authenticated probe endpoint: echoes the caller identity derived from the JWT.
 * Useful for confirming the whole auth path works (and as a manual-test target).
 */
@RestController
@RequestMapping("/api")
class MeController {
    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal jwt: Jwt,
    ): MeResponse = MeResponse(userId = jwt.userId, email = jwt.getClaimAsString("email"))
}

data class MeResponse(
    val userId: UUID,
    val email: String?,
)
