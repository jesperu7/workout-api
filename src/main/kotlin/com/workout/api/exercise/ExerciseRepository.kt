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

    fun findAll(
        category: String?,
        measurementType: MeasurementType?,
        limit: Int,
        offset: Int,
    ): List<Exercise> {
        // WHERE is assembled from FIXED fragments based on which filters are present;
        // values are always bound as named parameters (never interpolated).
        val conditions =
            buildList {
                if (category != null) add("category = :category")
                if (measurementType != null) add("measurement_type = cast(:measurementType as measurement_type)")
            }
        val where = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        val sql =
            """
            SELECT id, name, category, measurement_type, created_by, created_at
            FROM exercises
            $where
            ORDER BY name
            LIMIT :limit OFFSET :offset
            """.trimIndent()

        var spec = jdbc.sql(sql).param("limit", limit).param("offset", offset)
        if (category != null) spec = spec.param("category", category)
        if (measurementType != null) spec = spec.param("measurementType", measurementType.dbValue)
        return spec.query(rowMapper).list()
    }

    fun findById(id: UUID): Exercise? =
        jdbc
            .sql(
                """
                SELECT id, name, category, measurement_type, created_by, created_at
                FROM exercises
                WHERE id = :id
                """.trimIndent(),
            ).param("id", id)
            .query(rowMapper)
            .optional()
            .orElse(null)
}
