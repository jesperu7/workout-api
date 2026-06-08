package com.workout.api.exercise

import com.workout.api.common.NotFoundException
import com.workout.api.common.Page
import com.workout.api.measurement.MeasurementType
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * The exercise catalog is GLOBAL (shared reference data), so it is intentionally not
 * user-scoped — unlike workouts/sets, where ownership is enforced. When user-created
 * exercises arrive (v1.5), this is where the `created_by = me OR global` filter lands.
 */
@Service
class ExerciseService(
    private val exercises: ExerciseRepository,
) {
    fun list(
        category: String?,
        measurementType: MeasurementType?,
        limit: Int,
        offset: Int,
    ): Page<Exercise> {
        val window = limit.coerceIn(1, 200)
        val start = offset.coerceAtLeast(0)
        val items = exercises.findAll(category, measurementType, window, start)
        return Page(items, window, start, exercises.count(category, measurementType))
    }

    fun byId(id: UUID): Exercise = exercises.findById(id) ?: throw NotFoundException("exercise $id not found")
}
