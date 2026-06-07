package com.workout.api.exercise

import com.workout.api.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ExerciseApiTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var exercises: ExerciseRepository

    // Any authenticated user can browse the global catalog.
    private fun authed() = jwt().jwt { it.subject("2e2e9f86-42aa-42ed-9abd-86d284a33998") }

    @Test
    fun `listing requires authentication`() {
        mvc.get("/api/exercises").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `lists the full seeded catalog`() {
        mvc.get("/api/exercises") { with(authed()) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(25) }
        }
    }

    @Test
    fun `filters by measurement type`() {
        mvc.get("/api/exercises?measurementType=WEIGHT_REPS") { with(authed()) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(11) }
        }
    }

    @Test
    fun `filters by category`() {
        mvc.get("/api/exercises?category=push") { with(authed()) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(6) }
        }
    }

    @Test
    fun `gets one exercise by id`() {
        val ex = exercises.findAll(null, null, 1, 0).first()
        mvc.get("/api/exercises/${ex.id}") { with(authed()) }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(ex.id.toString()) }
            jsonPath("$.name") { value(ex.name) }
            jsonPath("$.global") { value(true) }
        }
    }

    @Test
    fun `unknown id returns 404`() {
        mvc.get("/api/exercises/00000000-0000-0000-0000-000000000000") { with(authed()) }.andExpect {
            status { isNotFound() }
        }
    }
}
