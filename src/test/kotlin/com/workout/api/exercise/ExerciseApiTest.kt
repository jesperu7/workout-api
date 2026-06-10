package com.workout.api.exercise

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
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ExerciseApiTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var exercises: ExerciseRepository

    private val userA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val userB = UUID.fromString("22222222-2222-2222-2222-222222222222")

    private fun asUser(id: UUID) = jwt().jwt { it.subject(id.toString()) }

    // These two run the REAL role mapping (SupabaseRoleConverter) over the mock JWT, so
    // the gate tests prove the claim->role logic — a plain jwt() mock gets default
    // authorities and would bypass it.
    private fun asMember(id: UUID) =
        jwt()
            .jwt { it.subject(id.toString()).claim("is_anonymous", false) }
            .authorities(SupabaseRoleConverter())

    private fun asGuest(id: UUID) =
        jwt()
            .jwt { it.subject(id.toString()).claim("is_anonymous", true) }
            .authorities(SupabaseRoleConverter())

    @BeforeEach
    fun reset() {
        // workouts first — their cascade clears workout_exercises/sets that could
        // otherwise hold FK references to the user-created exercises we delete next.
        jdbc.sql("DELETE FROM workouts").update()
        jdbc.sql("DELETE FROM exercises WHERE created_by IS NOT NULL").update()
        listOf(userA, userB).forEach { id ->
            jdbc
                .sql("INSERT INTO auth.users (id, email) VALUES (:id, :email) ON CONFLICT (id) DO NOTHING")
                .param("id", id)
                .param("email", "$id@test.local")
                .update()
        }
    }

    private fun createExercise(
        auth: RequestPostProcessor,
        body: String,
    ): ResultActionsDsl =
        mvc.post("/api/exercises") {
            with(auth)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun idFrom(body: String) = Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]

    // ---- browsing the catalog ----

    @Test
    fun `listing requires authentication`() {
        mvc.get("/api/exercises").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `lists the full seeded catalog`() {
        mvc.get("/api/exercises") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(25) }
            jsonPath("$.total") { value(25) }
        }
    }

    @Test
    fun `filters by measurement type`() {
        mvc.get("/api/exercises?measurementType=WEIGHT_REPS") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(11) }
            jsonPath("$.total") { value(11) }
        }
    }

    @Test
    fun `filters by category`() {
        mvc.get("/api/exercises?category=push") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(6) }
            jsonPath("$.total") { value(6) }
        }
    }

    @Test
    fun `searches by name case-insensitively`() {
        // seeded matches: Leg Press, Bench Press, Overhead Press, Incline Bench Press
        mvc.get("/api/exercises?name=press") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(4) }
            jsonPath("$.total") { value(4) }
        }
    }

    @Test
    fun `gets one exercise by id`() {
        val ex = exercises.findAll(userA, null, null, null, 1, 0).first()
        mvc.get("/api/exercises/${ex.id}") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(ex.id.toString()) }
            jsonPath("$.name") { value(ex.name) }
            jsonPath("$.global") { value(true) }
        }
    }

    @Test
    fun `unknown id returns 404`() {
        mvc.get("/api/exercises/00000000-0000-0000-0000-000000000000") { with(asUser(userA)) }.andExpect {
            status { isNotFound() }
        }
    }

    // ---- user-created exercises [v1.1] ----

    @Test
    fun `creating requires authentication`() {
        mvc
            .post("/api/exercises") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"X","measurementType":"BODYWEIGHT"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `creating an exercise returns 201 and it appears in my catalog`() {
        createExercise(asMember(userA), """{"name":"Cable Crossover","category":"push","measurementType":"WEIGHT_REPS"}""")
            .andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("Cable Crossover") }
                jsonPath("$.category") { value("push") }
                jsonPath("$.measurementType") { value("WEIGHT_REPS") }
                jsonPath("$.global") { value(false) }
            }

        mvc.get("/api/exercises?name=crossover") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(1) }
            jsonPath("$.items[0].name") { value("Cable Crossover") }
        }
    }

    @Test
    fun `created exercises are private to their owner`() {
        val body =
            createExercise(asMember(userA), """{"name":"Secret Move","measurementType":"BODYWEIGHT"}""")
                .andReturn()
                .response.contentAsString
        val id = idFrom(body)

        mvc.get("/api/exercises/$id") { with(asUser(userA)) }.andExpect { status { isOk() } }

        // user B: not listed, and 404 by id
        mvc.get("/api/exercises") { with(asUser(userB)) }.andExpect {
            jsonPath("$.total") { value(25) }
        }
        mvc.get("/api/exercises/$id") { with(asUser(userB)) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `duplicate name for the same user is a 409 conflict`() {
        createExercise(asMember(userA), """{"name":"Sled Push","measurementType":"WEIGHT_REPS"}""")
            .andExpect { status { isCreated() } }
        // case-insensitive — the unique index is on lower(name)
        createExercise(asMember(userA), """{"name":"sled push","measurementType":"WEIGHT_REPS"}""")
            .andExpect { status { isConflict() } }
        // a different user is free to use the same name
        createExercise(asMember(userB), """{"name":"Sled Push","measurementType":"WEIGHT_REPS"}""")
            .andExpect { status { isCreated() } }
    }

    @Test
    fun `a user may shadow a global name`() {
        // "Bench Press" is seeded globally; per-user uniqueness doesn't cross the
        // global/user partitions — the name search is the client-side guard.
        createExercise(asMember(userA), """{"name":"Bench Press","measurementType":"WEIGHT_REPS"}""")
            .andExpect { status { isCreated() } }
    }

    @Test
    fun `blank name is rejected`() {
        createExercise(asMember(userA), """{"name":"   ","measurementType":"WEIGHT_REPS"}""")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `missing measurement type is rejected`() {
        createExercise(asMember(userA), """{"name":"Goblet Squat"}""")
            .andExpect { status { isBadRequest() } }
    }

    // ---- guest gating [v1.1] ----

    @Test
    fun `a guest cannot create an exercise`() {
        createExercise(asGuest(userA), """{"name":"Guest Move","measurementType":"BODYWEIGHT"}""")
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `a token without the anonymity claim is treated as guest`() {
        // defensive default: claim missing -> ROLE_GUEST -> member-only routes refuse
        val noClaim = jwt().jwt { it.subject(userA.toString()) }.authorities(SupabaseRoleConverter())
        createExercise(noClaim, """{"name":"No Claim Move","measurementType":"BODYWEIGHT"}""")
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `a guest can still browse the catalog`() {
        mvc.get("/api/exercises") { with(asGuest(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(25) }
        }
    }
}
