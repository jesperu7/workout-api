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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** The API's error contract: domain and framework errors alike are RFC 7807 problem+json. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ErrorContractTest {
    @Autowired
    lateinit var mvc: MockMvc

    private fun authed() = jwt().jwt { it.subject("11111111-1111-1111-1111-111111111111") }

    @Test
    fun `not found is 404 problem+json`() {
        mvc.get("/api/exercises/00000000-0000-0000-0000-000000000000") { with(authed()) }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(404) }
        }
    }

    @Test
    fun `bean-validation failure is 400 problem+json`() {
        // exerciseId is @NotNull -> validation fails before the handler body runs
        mvc
            .post("/api/workouts/11111111-1111-1111-1111-111111111111/exercises") {
                with(authed())
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            }
    }

    @Test
    fun `malformed json body is 400`() {
        mvc
            .post("/api/workouts") {
                with(authed())
                contentType = MediaType.APPLICATION_JSON
                content = "{ not json"
            }.andExpect { status { isBadRequest() } }
    }
}
