package com.workout.api.history

import com.workout.api.TestcontainersConfiguration
import com.workout.api.exercise.ExerciseRepository
import com.workout.api.measurement.MeasurementType
import com.workout.api.measurement.SetMeasurement
import com.workout.api.workout.WorkoutExerciseRepository
import com.workout.api.workout.WorkoutRepository
import com.workout.api.workout.WorkoutSetRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class HistoryApiTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var exercises: ExerciseRepository

    @Autowired
    lateinit var workouts: WorkoutRepository

    @Autowired
    lateinit var workoutExercises: WorkoutExerciseRepository

    @Autowired
    lateinit var sets: WorkoutSetRepository

    private val userA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val userB = UUID.fromString("22222222-2222-2222-2222-222222222222")

    private fun asUser(id: UUID) = jwt().jwt { it.subject(id.toString()) }

    private lateinit var benchId: UUID

    @BeforeEach
    fun seed() {
        jdbc.sql("DELETE FROM workouts").update()
        listOf(userA, userB).forEach { id ->
            jdbc
                .sql("INSERT INTO auth.users (id, email) VALUES (:id, :email) ON CONFLICT (id) DO NOTHING")
                .param("id", id)
                .param("email", "$id@test.local")
                .update()
        }
        benchId = exercises.findAll(null, MeasurementType.WEIGHT_REPS, 1, 0).first().id

        // userA Bench Press: May 1 -> 100x5, 100x5, 100x4 ; May 8 -> 102.5x5
        val d1 = OffsetDateTime.parse("2026-05-01T17:00:00Z")
        val d2 = OffsetDateTime.parse("2026-05-08T17:00:00Z")
        val we = workoutExercises.insert(workouts.insert(userA, d1, null).id, benchId, 0, null)
        var idx = 0

        fun logSet(
            weight: String,
            reps: Int,
            at: OffsetDateTime,
        ) = sets.insert(
            we.id,
            userA,
            benchId,
            MeasurementType.WEIGHT_REPS,
            idx++,
            at,
            SetMeasurement(weight = BigDecimal(weight), reps = reps),
        )
        logSet("100", 5, d1)
        logSet("100", 5, d1)
        logSet("100", 4, d1)
        logSet("102.5", 5, d2)
    }

    @Test
    fun `history requires authentication`() {
        mvc.get("/api/exercises/$benchId/history").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `reps history at a given weight`() {
        mvc.get("/api/exercises/$benchId/history?weight=100") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) } // reps 5, 5, 4 at 100 kg
        }
    }

    @Test
    fun `weight history at a given rep count`() {
        mvc.get("/api/exercises/$benchId/history?reps=5") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) } // 100, 100, 102.5 at 5 reps
        }
    }

    @Test
    fun `full history over time`() {
        mvc.get("/api/exercises/$benchId/history") { with(asUser(userA)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(4) }
        }
    }

    @Test
    fun `history is scoped to the caller`() {
        mvc.get("/api/exercises/$benchId/history") { with(asUser(userB)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) } // user B logged nothing
        }
    }

    @Test
    fun `history for an unknown exercise is 404`() {
        mvc.get("/api/exercises/00000000-0000-0000-0000-000000000000/history") { with(asUser(userA)) }.andExpect {
            status { isNotFound() }
        }
    }
}
