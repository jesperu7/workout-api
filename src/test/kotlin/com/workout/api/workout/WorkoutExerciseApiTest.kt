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
class WorkoutExerciseApiTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var exercises: ExerciseRepository

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

    private fun anExerciseId(): UUID = exercises.findAll(userA, null, null, null, 1, 0).first().id

    private fun idFrom(body: String) = Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]

    private fun createWorkout(user: UUID): String =
        idFrom(
            mvc
                .post("/api/workouts") {
                    with(asUser(user))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"notes":"w"}"""
                }.andReturn()
                .response.contentAsString,
        )

    private fun addExercise(
        user: UUID,
        workoutId: String,
        exerciseId: UUID,
    ): String =
        idFrom(
            mvc
                .post("/api/workouts/$workoutId/exercises") {
                    with(asUser(user))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"exerciseId":"$exerciseId","notes":"n"}"""
                }.andReturn()
                .response.contentAsString,
        )

    @Test
    fun `add an exercise to my workout, then list it`() {
        val w = createWorkout(userA)
        val ex = anExerciseId()
        mvc
            .post("/api/workouts/$w/exercises") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"exerciseId":"$ex","notes":"first"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.exerciseId") { value(ex.toString()) }
            }

        mvc.get("/api/workouts/$w/exercises") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `cannot add to another users workout`() {
        val w = createWorkout(userA)
        val ex = anExerciseId()
        mvc
            .post("/api/workouts/$w/exercises") {
                with(asUser(userB))
                contentType = MediaType.APPLICATION_JSON
                content = """{"exerciseId":"$ex"}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `cannot add another users exercise to my workout`() {
        // user B's private exercise is invisible to A — attaching it must 404,
        // same as an id that doesn't exist at all.
        val bPrivate = exercises.insert("B Private Move", null, MeasurementType.BODYWEIGHT, userB)
        val w = createWorkout(userA)
        mvc
            .post("/api/workouts/$w/exercises") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"exerciseId":"${bPrivate.id}"}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `adding a nonexistent exercise returns 404`() {
        val w = createWorkout(userA)
        mvc
            .post("/api/workouts/$w/exercises") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"exerciseId":"00000000-0000-0000-0000-000000000000"}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `update and delete my workout exercise`() {
        val w = createWorkout(userA)
        val we = addExercise(userA, w, anExerciseId())
        mvc
            .patch("/api/workout-exercises/$we") {
                with(asUser(userA))
                contentType = MediaType.APPLICATION_JSON
                content = """{"position":3,"notes":"x"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.position") { value(3) }
            }
        mvc.delete("/api/workout-exercises/$we") { with(asUser(userA)) }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `cannot touch another users workout exercise`() {
        val w = createWorkout(userA)
        val we = addExercise(userA, w, anExerciseId())
        mvc
            .patch("/api/workout-exercises/$we") {
                with(asUser(userB))
                contentType = MediaType.APPLICATION_JSON
                content = """{"notes":"x"}"""
            }.andExpect { status { isNotFound() } }
        mvc.delete("/api/workout-exercises/$we") { with(asUser(userB)) }.andExpect { status { isNotFound() } }
    }
}
