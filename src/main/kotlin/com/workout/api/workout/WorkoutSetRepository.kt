package com.workout.api.workout

import com.workout.api.measurement.MeasurementType
import com.workout.api.measurement.SetMeasurement
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class WorkoutSetRepository(
    private val jdbc: JdbcClient,
) {
    private val cols =
        "id, workout_exercise_id, user_id, exercise_id, measurement_type, set_index, " +
            "performed_at, weight, reps, added_weight, duration_seconds, distance_meters, rpe, program_set_id"

    private val rowMapper =
        RowMapper { rs, _ ->
            WorkoutSet(
                id = rs.getObject("id", UUID::class.java),
                workoutExerciseId = rs.getObject("workout_exercise_id", UUID::class.java),
                userId = rs.getObject("user_id", UUID::class.java),
                exerciseId = rs.getObject("exercise_id", UUID::class.java),
                measurementType = MeasurementType.fromDbValue(rs.getString("measurement_type")),
                setIndex = rs.getInt("set_index"),
                performedAt = rs.getObject("performed_at", OffsetDateTime::class.java),
                weight = rs.getBigDecimal("weight"),
                reps = rs.getObject("reps") as Int?,
                addedWeight = rs.getBigDecimal("added_weight"),
                durationSeconds = rs.getObject("duration_seconds") as Int?,
                distanceMeters = rs.getBigDecimal("distance_meters"),
                rpe = rs.getBigDecimal("rpe"),
                programSetId = rs.getObject("program_set_id", UUID::class.java),
            )
        }

    fun insert(
        workoutExerciseId: UUID,
        userId: UUID,
        exerciseId: UUID,
        measurementType: MeasurementType,
        setIndex: Int,
        performedAt: OffsetDateTime,
        m: SetMeasurement,
    ): WorkoutSet =
        jdbc
            .sql(
                """
                INSERT INTO sets (workout_exercise_id, user_id, exercise_id, measurement_type, set_index, performed_at,
                                  weight, reps, added_weight, duration_seconds, distance_meters, rpe)
                VALUES (:weId, :userId, :exerciseId, cast(:measurementType as measurement_type), :setIndex, :performedAt,
                        :weight, :reps, :addedWeight, :durationSeconds, :distanceMeters, :rpe)
                RETURNING $cols
                """.trimIndent(),
            ).param("weId", workoutExerciseId)
            .param("userId", userId)
            .param("exerciseId", exerciseId)
            .param("measurementType", measurementType.dbValue)
            .param("setIndex", setIndex)
            .param("performedAt", performedAt)
            .param("weight", m.weight)
            .param("reps", m.reps)
            .param("addedWeight", m.addedWeight)
            .param("durationSeconds", m.durationSeconds)
            .param("distanceMeters", m.distanceMeters)
            .param("rpe", m.rpe)
            .query(rowMapper)
            .single()

    fun findByIdForUser(
        id: UUID,
        userId: UUID,
    ): WorkoutSet? =
        jdbc
            .sql("SELECT $cols FROM sets WHERE id = :id AND user_id = :userId")
            .param("id", id)
            .param("userId", userId)
            .query(rowMapper)
            .optional()
            .orElse(null)

    fun findAllForWorkoutExercise(workoutExerciseId: UUID): List<WorkoutSet> =
        jdbc
            .sql("SELECT $cols FROM sets WHERE workout_exercise_id = :weId ORDER BY set_index, id")
            .param("weId", workoutExerciseId)
            .query(rowMapper)
            .list()

    fun update(
        id: UUID,
        userId: UUID,
        setIndex: Int,
        performedAt: OffsetDateTime,
        m: SetMeasurement,
    ): WorkoutSet? =
        jdbc
            .sql(
                """
                UPDATE sets
                SET set_index = :setIndex, performed_at = :performedAt,
                    weight = :weight, reps = :reps, added_weight = :addedWeight,
                    duration_seconds = :durationSeconds, distance_meters = :distanceMeters, rpe = :rpe
                WHERE id = :id AND user_id = :userId
                RETURNING $cols
                """.trimIndent(),
            ).param("setIndex", setIndex)
            .param("performedAt", performedAt)
            .param("weight", m.weight)
            .param("reps", m.reps)
            .param("addedWeight", m.addedWeight)
            .param("durationSeconds", m.durationSeconds)
            .param("distanceMeters", m.distanceMeters)
            .param("rpe", m.rpe)
            .param("id", id)
            .param("userId", userId)
            .query(rowMapper)
            .optional()
            .orElse(null)

    fun deleteForUser(
        id: UUID,
        userId: UUID,
    ): Boolean =
        jdbc
            .sql("DELETE FROM sets WHERE id = :id AND user_id = :userId")
            .param("id", id)
            .param("userId", userId)
            .update() > 0
}
