package com.workout.api.exercise

import com.workout.api.measurement.MeasurementType
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class ExerciseRepository(
    private val jdbc: JdbcClient,
) {
    private val columns = "id, name, category, measurement_type, created_by, created_at"

    // Visibility rule (the RLS spec in schema.sql): global rows OR the user's own.
    // Every read filters by this — created_by is never exposed to the caller's control.
    private val visibleToUser = "(created_by IS NULL OR created_by = :userId)"

    private val rowMapper =
        RowMapper { rs, _ ->
            Exercise(
                id = rs.getObject("id", UUID::class.java),
                name = rs.getString("name"),
                category = rs.getString("category"),
                measurementType = MeasurementType.fromDbValue(rs.getString("measurement_type")),
                createdBy = rs.getObject("created_by", UUID::class.java),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            )
        }

    // WHERE is assembled from FIXED fragments based on which filters are present;
    // values are always bound as named parameters (never interpolated).
    private fun filterConditions(
        category: String?,
        measurementType: MeasurementType?,
        name: String?,
    ): String =
        buildList {
            add(visibleToUser)
            if (category != null) add("category = :category")
            if (measurementType != null) add("measurement_type = cast(:measurementType as measurement_type)")
            // substring search; ILIKE = case-insensitive LIKE (Postgres)
            if (name != null) add("name ILIKE '%' || :name || '%'")
        }.joinToString(" AND ")

    private fun JdbcClient.StatementSpec.bindFilters(
        userId: UUID,
        category: String?,
        measurementType: MeasurementType?,
        name: String?,
    ): JdbcClient.StatementSpec {
        var spec = param("userId", userId)
        if (category != null) spec = spec.param("category", category)
        if (measurementType != null) spec = spec.param("measurementType", measurementType.dbValue)
        if (name != null) spec = spec.param("name", name)
        return spec
    }

    fun findAll(
        userId: UUID,
        category: String?,
        measurementType: MeasurementType?,
        name: String?,
        limit: Int,
        offset: Int,
    ): List<Exercise> =
        jdbc
            .sql(
                """
                SELECT $columns
                FROM exercises
                WHERE ${filterConditions(category, measurementType, name)}
                ORDER BY name
                LIMIT :limit OFFSET :offset
                """.trimIndent(),
            ).bindFilters(userId, category, measurementType, name)
            .param("limit", limit)
            .param("offset", offset)
            .query(rowMapper)
            .list()

    fun count(
        userId: UUID,
        category: String?,
        measurementType: MeasurementType?,
        name: String?,
    ): Long =
        jdbc
            .sql("SELECT count(*) FROM exercises WHERE ${filterConditions(category, measurementType, name)}")
            .bindFilters(userId, category, measurementType, name)
            .query(Long::class.javaObjectType)
            .single()

    fun findById(
        id: UUID,
        userId: UUID,
    ): Exercise? =
        jdbc
            .sql(
                """
                SELECT $columns
                FROM exercises
                WHERE id = :id AND $visibleToUser
                """.trimIndent(),
            ).param("id", id)
            .param("userId", userId)
            .query(rowMapper)
            .optional()
            .orElse(null)

    fun insert(
        name: String,
        category: String?,
        measurementType: MeasurementType,
        createdBy: UUID,
    ): Exercise =
        jdbc
            .sql(
                """
                INSERT INTO exercises (name, category, measurement_type, created_by)
                VALUES (:name, :category, cast(:measurementType as measurement_type), :createdBy)
                RETURNING $columns
                """.trimIndent(),
            ).param("name", name)
            .param("category", category)
            .param("measurementType", measurementType.dbValue)
            .param("createdBy", createdBy)
            .query(rowMapper)
            .single()
}
