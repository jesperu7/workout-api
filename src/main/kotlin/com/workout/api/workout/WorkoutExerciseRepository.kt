package com.workout.api.workout

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Ownership is transitive (via the parent workout), enforced by joining `workouts` and
 * filtering `w.user_id`. The service still checks the parent workout up front for clear 404s.
 */
@Repository
class WorkoutExerciseRepository(
    private val jdbc: JdbcClient,
) {
    private val rowMapper =
        RowMapper { rs, _ ->
            WorkoutExercise(
                id = rs.getObject("id", UUID::class.java),
                workoutId = rs.getObject("workout_id", UUID::class.java),
                exerciseId = rs.getObject("exercise_id", UUID::class.java),
                position = rs.getInt("position"),
                notes = rs.getString("notes"),
            )
        }

    fun insert(
        workoutId: UUID,
        exerciseId: UUID,
        position: Int,
        notes: String?,
    ): WorkoutExercise =
        jdbc
            .sql(
                """
                INSERT INTO workout_exercises (workout_id, exercise_id, position, notes)
                VALUES (:workoutId, :exerciseId, :position, :notes)
                RETURNING id, workout_id, exercise_id, position, notes
                """.trimIndent(),
            ).param("workoutId", workoutId)
            .param("exerciseId", exerciseId)
            .param("position", position)
            .param("notes", notes)
            .query(rowMapper)
            .single()

    fun findByIdForUser(
        id: UUID,
        userId: UUID,
    ): WorkoutExercise? =
        jdbc
            .sql(
                """
                SELECT we.id, we.workout_id, we.exercise_id, we.position, we.notes
                FROM workout_exercises we
                JOIN workouts w ON w.id = we.workout_id
                WHERE we.id = :id AND w.user_id = :userId
                """.trimIndent(),
            ).param("id", id)
            .param("userId", userId)
            .query(rowMapper)
            .optional()
            .orElse(null)

    fun findAllForWorkout(workoutId: UUID): List<WorkoutExercise> =
        jdbc
            .sql(
                """
                SELECT id, workout_id, exercise_id, position, notes
                FROM workout_exercises
                WHERE workout_id = :workoutId
                ORDER BY position, id
                """.trimIndent(),
            ).param("workoutId", workoutId)
            .query(rowMapper)
            .list()

    fun update(
        id: UUID,
        userId: UUID,
        position: Int,
        notes: String?,
    ): WorkoutExercise? =
        jdbc
            .sql(
                """
                UPDATE workout_exercises we
                SET position = :position, notes = :notes
                FROM workouts w
                WHERE we.id = :id AND w.id = we.workout_id AND w.user_id = :userId
                RETURNING we.id, we.workout_id, we.exercise_id, we.position, we.notes
                """.trimIndent(),
            ).param("position", position)
            .param("notes", notes)
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
            .sql(
                """
                DELETE FROM workout_exercises we
                USING workouts w
                WHERE we.id = :id AND w.id = we.workout_id AND w.user_id = :userId
                """.trimIndent(),
            ).param("id", id)
            .param("userId", userId)
            .update() > 0
}
