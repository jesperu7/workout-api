package com.workout.api.exercise

import com.workout.api.common.ConflictException
import com.workout.api.common.NotFoundException
import com.workout.api.common.Page
import com.workout.api.exercise.dto.CreateExerciseRequest
import com.workout.api.measurement.MeasurementType
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * The exercise catalog mixes GLOBAL rows (created_by NULL, shared reference data) with
 * user-created rows [v1.1]. Reads are visibility-scoped to "global OR mine" — the RLS
 * spec in schema.sql, enforced here in code because this service-role backend bypasses RLS.
 */
@Service
class ExerciseService(
    private val exercises: ExerciseRepository,
) {
    fun list(
        userId: UUID,
        category: String?,
        measurementType: MeasurementType?,
        name: String?,
        limit: Int,
        offset: Int,
    ): Page<Exercise> {
        val window = limit.coerceIn(1, 200)
        val start = offset.coerceAtLeast(0)
        val items = exercises.findAll(userId, category, measurementType, name, window, start)
        return Page(items, window, start, exercises.count(userId, category, measurementType, name))
    }

    fun byId(
        userId: UUID,
        id: UUID,
    ): Exercise = exercises.findById(id, userId) ?: throw NotFoundException("exercise $id not found")

    fun create(
        userId: UUID,
        req: CreateExerciseRequest,
    ): Exercise {
        val name = requireNotNull(req.name) { "name is required" }.trim()
        val measurementType = requireNotNull(req.measurementType) { "measurementType is required" }
        return try {
            exercises.insert(name, req.category, measurementType, userId)
        } catch (_: DuplicateKeyException) {
            // The partial unique index (V3) is the real enforcement; reacting to the
            // violation instead of pre-checking avoids a check-then-insert race.
            throw ConflictException("you already have an exercise named \"$name\"")
        }
    }
}
