package com.workout.api.config

import com.workout.api.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Confirms springdoc actually boots under Spring Boot 4 and the spec is public + complete.
 * (If springdoc were Boot-4-incompatible, the context wouldn't load and this would fail.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class OpenApiDocsTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `openapi spec is public and documents our endpoints`() {
        mvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.openapi") { exists() }
            jsonPath("$.paths['/api/me']") { exists() }
            jsonPath("$.paths['/api/exercises/{exerciseId}/history']") { exists() }
        }
    }
}
