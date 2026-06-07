package com.workout.api.workout

import com.workout.api.TestcontainersConfiguration
import com.workout.api.exercise.ExerciseRepository
import com.workout.api.measurement.MeasurementType
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
class SetApiTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var exercises: ExerciseRepository

    private val userA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val userB = UUID.fromString("22222222-2222-2222-2222-222222222222")

    private fun asUser(id: UUID) = jwt().jwt { it.subject(id.toString()) }

    private fun idFrom(body: String) = Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]

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

    private fun weightRepsExerciseId(): UUID = exercises.findAll(null, MeasurementType.WEIGHT_REPS, 1, 0).first().id

    /** Create a workout and add a weight_reps exercise to it; return the workout_exercise id. */
    private fun weightRepsWorkoutExercise(user: UUID): String {
        val workout =
            idFrom(
                mvc
                    .post("/api/workouts") {
                        with(asUser(user))
                        contentType = MediaType.APPLICATION_JSON
                        content = "{}"
                    }.andReturn()
                    .response.contentAsString,
            )
        return idFrom(
            mvc
                .post("/api/workouts/$workout/exercises") {
                    with(asUser(user))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"exerciseId":"${weightRepsExerciseId()}"}"""
                }.andReturn()
                .response.contentAsString,
        )
    }

    @Test
    fun `log a valid weight_reps set then list it`() {
        val we = weightRepsWorkoutExercise(userA)
        mvc
            .post("/api/workout-exercises/$we/sets") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"weight":100.5,"reps":5,"rpe":8}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.reps") { value(5) }
                jsonPath("$.measurementType") { value("WEIGHT_REPS") }
            }
        mvc.get("/api/workout-exercises/$we/sets") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `logging a weight_reps set without weight is 400`() {
        val we = weightRepsWorkoutExercise(userA)
        mvc
            .post("/api/workout-exercises/$we/sets") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"reps":5}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `cannot log a set to another users workout-exercise`() {
        val we = weightRepsWorkoutExercise(userA)
        mvc
            .post("/api/workout-exercises/$we/sets") {
                with(asUser(userB))
                contentType = MediaType.APPLICATION_JSON
                content = """{"weight":100,"reps":5}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `update my set and others cannot touch it`() {
        val we = weightRepsWorkoutExercise(userA)
        val setId =
            idFrom(
                mvc
                    .post("/api/workout-exercises/$we/sets") {
                        with(asUser(userA))
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"weight":100,"reps":5}"""
                    }.andReturn()
                    .response.contentAsString,
            )

        mvc
            .patch("/api/sets/$setId") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"reps":6}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.reps") { value(6) }
            }

        mvc
            .patch("/api/sets/$setId") {
                with(asUser(userB))
                contentType = MediaType.APPLICATION_JSON
                content = """{"reps":99}"""
            }.andExpect { status { isNotFound() } }
        mvc.delete("/api/sets/$setId") { with(asUser(userB)) }.andExpect { status { isNotFound() } }

        mvc.delete("/api/sets/$setId") { with(asUser(userA)) }.andExpect { status { isNoContent() } }
    }
}
