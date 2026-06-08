package com.workout.api.history

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/**
 * The headline bidirectional-history queries (schema.sql §6), always scoped to the user:
 *   - weight filter -> rep history at that weight  (index: sets_user_exercise_weight)
 *   - reps filter   -> weight history at that rep count (index: sets_user_exercise_reps)
 *   - neither       -> full history over time       (index: sets_user_exercise_time)
 * The WHERE is built from FIXED fragments; values are always bound as named parameters.
 */
@Repository
class HistoryRepository(
    private val jdbc: JdbcClient,
) {
    private val rowMapper =
        RowMapper { rs, _ ->
            SetHistoryPoint(
                performedAt = rs.getObject("performed_at", OffsetDateTime::class.java),
                weight = rs.getBigDecimal("weight"),
                reps = rs.getObject("reps") as Int?,
                addedWeight = rs.getBigDecimal("added_weight"),
                durationSeconds = rs.getObject("duration_seconds") as Int?,
                distanceMeters = rs.getBigDecimal("distance_meters"),
                rpe = rs.getBigDecimal("rpe"),
            )
        }

    fun forExercise(
        userId: UUID,
        exerciseId: UUID,
        weight: BigDecimal?,
        reps: Int?,
    ): List<SetHistoryPoint> {
        val conditions = mutableListOf("user_id = :userId", "exercise_id = :exerciseId")
        if (weight != null) conditions += "weight = :weight"
        if (reps != null) conditions += "reps = :reps"
        val sql =
            """
            SELECT performed_at, weight, reps, added_weight, duration_seconds, distance_meters, rpe
            FROM sets
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY performed_at
            """.trimIndent()

        var spec = jdbc.sql(sql).param("userId", userId).param("exerciseId", exerciseId)
        if (weight != null) spec = spec.param("weight", weight)
        if (reps != null) spec = spec.param("reps", reps)
        return spec.query(rowMapper).list()
    }
}
