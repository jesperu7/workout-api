package com.workout.api.workout

import com.workout.api.TestcontainersConfiguration
import com.workout.api.config.SupabaseRoleConverter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class WorkoutApiTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    private val userA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val userB = UUID.fromString("22222222-2222-2222-2222-222222222222")

    private fun asUser(id: UUID) = jwt().jwt { it.subject(id.toString()) }

    @BeforeEach
    fun reset() {
        jdbc.sql("DELETE FROM workouts").update()
        listOf(userA, userB).forEach { id ->
            jdbc
                .sql("INSERT INTO auth.users (id, email) VALUES (:id, :email) ON CONFLICT (id) DO NOTHING")
                .param("id", id)
                .param("email", "$id@test.local")
                .update()
        }
    }

    private fun createWorkout(
        user: UUID,
        notes: String,
    ): String {
        val body =
            mvc
                .post("/api/workouts") {
                    with(asUser(user))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"notes":"$notes"}"""
                }.andReturn()
                .response.contentAsString
        return Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    @Test
    fun `creating requires authentication`() {
        mvc
            .post("/api/workouts") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `create then read my workout`() {
        val id = createWorkout(userA, "push day")
        mvc.get("/api/workouts/$id") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id) }
            jsonPath("$.notes") { value("push day") }
        }
    }

    @Test
    fun `list returns only my workouts`() {
        createWorkout(userA, "a1")
        createWorkout(userA, "a2")
        createWorkout(userB, "b1")
        mvc.get("/api/workouts") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.total") { value(2) }
        }
    }

    @Test
    fun `update my workout`() {
        val id = createWorkout(userA, "old")
        mvc
            .patch("/api/workouts/$id") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"notes":"new"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.notes") { value("new") }
            }
    }

    @Test
    fun `delete my workout`() {
        val id = createWorkout(userA, "temp")
        mvc.delete("/api/workouts/$id") { with(asUser(userA)) }.andExpect { status { isNoContent() } }
        mvc.get("/api/workouts/$id") { with(asUser(userA)) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `guests can log workouts`() {
        // [v1.1] the member gate covers only catalog creation; anonymous accounts log normally
        val guest =
            jwt()
                .jwt { it.subject(userA.toString()).claim("is_anonymous", true) }
                .authorities(SupabaseRoleConverter())
        mvc
            .post("/api/workouts") {
                with(guest)
                contentType = MediaType.APPLICATION_JSON
                content = """{"notes":"guest session"}"""
            }.andExpect { status { isCreated() } }
    }

    @Test
    fun `cannot read, update or delete another users workout`() {
        val id = createWorkout(userA, "private")

        mvc.get("/api/workouts/$id") { with(asUser(userB)) }.andExpect { status { isNotFound() } }
        mvc
            .patch("/api/workouts/$id") {
                with(asUser(userB))
                contentType = MediaType.APPLICATION_JSON
                content = """{"notes":"hacked"}"""
            }.andExpect { status { isNotFound() } }
        mvc.delete("/api/workouts/$id") { with(asUser(userB)) }.andExpect { status { isNotFound() } }

        // A's workout is untouched
        mvc.get("/api/workouts/$id") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.notes") { value("private") }
        }
    }

    // ---- named workouts [v1.1] ----

    private fun createNamedWorkout(
        user: UUID,
        name: String,
    ): String {
        val body =
            mvc
                .post("/api/workouts") {
                    with(asUser(user))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"$name"}"""
                }.andReturn()
                .response.contentAsString
        return Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    @Test
    fun `create with a name trims and echoes it, without a name it is absent`() {
        mvc
            .post("/api/workouts") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"  Push Day  ","notes":"chest"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("Push Day") } // trimmed
            }
        // no name -> omitted from the response (Jackson non_null)
        val id = createWorkout(userA, "untitled")
        mvc.get("/api/workouts/$id") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.name") { doesNotExist() }
        }
    }

    @Test
    fun `PATCH sets the name, and a blank value clears it`() {
        val id = createWorkout(userA, "w")
        mvc
            .patch("/api/workouts/$id") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Leg Day"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Leg Day") }
            }
        mvc
            .patch("/api/workouts/$id") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"   "}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { doesNotExist() }
            }
    }

    @Test
    fun `PATCH without a name leaves it unchanged`() {
        val id = createNamedWorkout(userA, "Push Day")
        mvc
            .patch("/api/workouts/$id") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"notes":"more volume"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Push Day") }
            }
    }

    @Test
    fun `an over-length name is rejected`() {
        val tooLong = "x".repeat(201)
        mvc
            .post("/api/workouts") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"$tooLong"}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `list carries the name and filters by exact name`() {
        createNamedWorkout(userA, "Push")
        createNamedWorkout(userA, "Legs")
        createNamedWorkout(userA, "Push")
        // list includes the name
        mvc.get("/api/workouts") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(3) }
            jsonPath("$.items[0].name") { exists() }
        }
        // exact-match filter — the client's "repeat last workout" query (newest first)
        mvc.get("/api/workouts?name=Push&limit=1") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(2) }
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].name") { value("Push") }
        }
    }
}
