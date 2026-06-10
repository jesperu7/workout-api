package com.workout.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * Stateless JWT resource-server security.
 *
 * The JWT decoder itself is auto-configured from `spring.security.oauth2.resourceserver.jwt`
 * (jwk-set-uri + audiences) — Supabase ES256 tokens are verified locally against the JWKS,
 * with no round trip to the auth server. We then read the user id from the `sub` claim
 * (see [com.workout.api.auth.userId]). Ownership/authorization is enforced per-request in services.
 *
 * [v1.1] Each token also gets exactly one role from [SupabaseRoleConverter]
 * (`is_anonymous` claim): ROLE_MEMBER for real accounts, ROLE_GUEST for anonymous
 * sign-ins. Route rules below gate member-only writes; everything else just needs
 * `authenticated`, so guests can browse and log workouts.
 */
@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val jwtConverter =
            JwtAuthenticationConverter().apply {
                setJwtGrantedAuthoritiesConverter(SupabaseRoleConverter())
            }
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize("/swagger-ui.html", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                // [v1.1] guests browse + log, but only real accounts create catalog content
                authorize(HttpMethod.POST, "/api/exercises", hasRole("MEMBER"))
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt { jwtAuthenticationConverter = jwtConverter }
            }
        }
        return http.build()
    }
}
