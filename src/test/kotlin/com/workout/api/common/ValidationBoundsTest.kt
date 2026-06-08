package com.workout.api.common

import com.workout.api.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * DTO bean-validation bounds reject bad input with 400 before the handler runs (so the path
 * ids here don't need to exist — validation happens during argument binding).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ValidationBoundsTest {
    @Autowired
    lateinit var mvc: MockMvc

    private val anyId = "00000000-0000-0000-0000-000000000001"

    private fun authed() = jwt().jwt { it.subject("11111111-1111-1111-1111-111111111111") }

    @Test
    fun `negative reps is rejected`() {
        mvc
            .post("/api/workout-exercises/$anyId/sets") {
                with(authed())
                contentType = MediaType.APPLICATION_JSON
                content = """{"weight":100,"reps":-1}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `rpe above 10 is rejected`() {
        mvc
            .post("/api/workout-exercises/$anyId/sets") {
                with(authed())
                contentType = MediaType.APPLICATION_JSON
                content = """{"weight":100,"reps":5,"rpe":11}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `negative position is rejected`() {
        mvc
            .post("/api/workouts/$anyId/exercises") {
                with(authed())
                contentType = MediaType.APPLICATION_JSON
                content = """{"exerciseId":"22222222-2222-2222-2222-222222222222","position":-1}"""
            }.andExpect { status { isBadRequest() } }
    }
}
