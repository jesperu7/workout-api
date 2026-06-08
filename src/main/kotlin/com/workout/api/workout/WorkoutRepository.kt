package com.workout.api.workout

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

/**
 * All reads/writes are scoped to `userId` (the owner). This is where the RLS spec from
 * schema.sql is enforced in code — a row owned by someone else is simply not matched.
 */
@Repository
class WorkoutRepository(
    private val jdbc: JdbcClient,
) {
    private val columns = "id, user_id, performed_at, notes, program_workout_id, created_at"

    private val rowMapper =
        RowMapper { rs, _ ->
            Workout(
                id = rs.getObject("id", UUID::class.java),
                userId = rs.getObject("user_id", UUID::class.java),
                performedAt = rs.getObject("performed_at", OffsetDateTime::class.java),
                notes = rs.getString("notes"),
                programWorkoutId = rs.getObject("program_workout_id", UUID::class.java),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            )
        }

    fun insert(
        userId: UUID,
        performedAt: OffsetDateTime,
        notes: String?,
    ): Workout =
        jdbc
            .sql(
                """
                INSERT INTO workouts (user_id, performed_at, notes)
                VALUES (:userId, :performedAt, :notes)
                RETURNING $columns
                """.trimIndent(),
            ).param("userId", userId)
            .param("performedAt", performedAt)
            .param("notes", notes)
            .query(rowMapper)
            .single()

    fun findByIdForUser(
        id: UUID,
        userId: UUID,
    ): Workout? =
        jdbc
            .sql("SELECT $columns FROM workouts WHERE id = :id AND user_id = :userId")
            .param("id", id)
            .param("userId", userId)
            .query(rowMapper)
            .optional()
            .orElse(null)

    fun findAllForUser(
        userId: UUID,
        limit: Int,
        offset: Int,
    ): List<Workout> =
        jdbc
            .sql(
                """
                SELECT $columns FROM workouts
                WHERE user_id = :userId
                ORDER BY performed_at DESC
                LIMIT :limit OFFSET :offset
                """.trimIndent(),
            ).param("userId", userId)
            .param("limit", limit)
            .param("offset", offset)
            .query(rowMapper)
            .list()

    fun countForUser(userId: UUID): Long =
        jdbc
            .sql("SELECT count(*) FROM workouts WHERE user_id = :userId")
            .param("userId", userId)
            .query(Long::class.javaObjectType)
            .single()

    fun update(
        id: UUID,
        userId: UUID,
        performedAt: OffsetDateTime,
        notes: String?,
    ): Workout? =
        jdbc
            .sql(
                """
                UPDATE workouts SET performed_at = :performedAt, notes = :notes
                WHERE id = :id AND user_id = :userId
                RETURNING $columns
                """.trimIndent(),
            ).param("performedAt", performedAt)
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
            .sql("DELETE FROM workouts WHERE id = :id AND user_id = :userId")
            .param("id", id)
            .param("userId", userId)
            .update() > 0
}
