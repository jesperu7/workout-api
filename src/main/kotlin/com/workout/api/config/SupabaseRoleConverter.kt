package com.workout.api.config

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Maps a verified Supabase JWT to exactly one app role, from the `is_anonymous`
 * claim Supabase puts on every access token:
 *
 *  - `ROLE_MEMBER` — real signed-in account (`is_anonymous` = false)
 *  - `ROLE_GUEST`  — anonymous session (`is_anonymous` = true), and — defensively —
 *    any token missing the claim. Same stance as Supabase's own RLS example, which
 *    treats an absent claim as not-a-member.
 *
 * Guests keep browsing and logging; the member-only routes are declared in
 * [SecurityConfig]. Converting a guest to a real account (sign up / link identity)
 * keeps the same user id, so everything they logged stays theirs.
 */
class SupabaseRoleConverter : Converter<Jwt, Collection<GrantedAuthority>> {
    override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
        val anonymous = jwt.getClaimAsBoolean("is_anonymous") ?: true
        return listOf(SimpleGrantedAuthority(if (anonymous) "ROLE_GUEST" else "ROLE_MEMBER"))
    }
}
