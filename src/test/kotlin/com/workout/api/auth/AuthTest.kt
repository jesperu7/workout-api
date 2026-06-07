package com.workout.api.auth

import com.workout.api.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Auth wiring tests. We don't run GoTrue here — `jwt()` injects a pre-validated principal,
 * so these exercise our SecurityFilterChain + principal mapping without a real token/network.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class AuthTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `me requires authentication`() {
        mvc.get("/api/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `me echoes the user id and email from the jwt`() {
        val userId = "2e2e9f86-42aa-42ed-9abd-86d284a33998"
        mvc
            .get("/api/me") {
                with(jwt().jwt { it.subject(userId).claim("email", "dev@workout.test") })
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(userId) }
                jsonPath("$.email") { value("dev@workout.test") }
            }
    }
}
