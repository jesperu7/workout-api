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
    private val columns = "id, user_id, performed_at, name, notes, program_workout_id, created_at"

    private val rowMapper =
        RowMapper { rs, _ ->
            Workout(
                id = rs.getObject("id", UUID::class.java),
                userId = rs.getObject("user_id", UUID::class.java),
                performedAt = rs.getObject("performed_at", OffsetDateTime::class.java),
                name = rs.getString("name"),
                notes = rs.getString("notes"),
                programWorkoutId = rs.getObject("program_workout_id", UUID::class.java),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            )
        }

    fun insert(
        userId: UUID,
        performedAt: OffsetDateTime,
        name: String?,
        notes: String?,
    ): Workout =
        jdbc
            .sql(
                """
                INSERT INTO workouts (user_id, performed_at, name, notes)
                VALUES (:userId, :performedAt, :name, :notes)
                RETURNING $columns
                """.trimIndent(),
            ).param("userId", userId)
            .param("performedAt", performedAt)
            .param("name", name)
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

    // Optional exact-match name filter (the client's "repeat last workout"). Fixed SQL
    // fragment chosen by presence of the filter; the value is always a bound named param.
    fun findAllForUser(
        userId: UUID,
        name: String?,
        limit: Int,
        offset: Int,
    ): List<Workout> {
        val nameFilter = if (name != null) " AND name = :name" else ""
        var spec =
            jdbc
                .sql(
                    """
                    SELECT $columns FROM workouts
                    WHERE user_id = :userId$nameFilter
                    ORDER BY performed_at DESC
                    LIMIT :limit OFFSET :offset
                    """.trimIndent(),
                ).param("userId", userId)
                .param("limit", limit)
                .param("offset", offset)
        if (name != null) spec = spec.param("name", name)
        return spec.query(rowMapper).list()
    }

    fun countForUser(
        userId: UUID,
        name: String?,
    ): Long {
        val nameFilter = if (name != null) " AND name = :name" else ""
        var spec = jdbc.sql("SELECT count(*) FROM workouts WHERE user_id = :userId$nameFilter").param("userId", userId)
        if (name != null) spec = spec.param("name", name)
        return spec.query(Long::class.javaObjectType).single()
    }

    fun update(
        id: UUID,
        userId: UUID,
        performedAt: OffsetDateTime,
        name: String?,
        notes: String?,
    ): Workout? =
        jdbc
            .sql(
                """
                UPDATE workouts SET performed_at = :performedAt, name = :name, notes = :notes
                WHERE id = :id AND user_id = :userId
                RETURNING $columns
                """.trimIndent(),
            ).param("performedAt", performedAt)
            .param("name", name)
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
