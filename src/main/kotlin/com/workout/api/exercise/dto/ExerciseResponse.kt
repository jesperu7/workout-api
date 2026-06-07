package com.workout.api.exercise.dto

import com.workout.api.exercise.Exercise
import com.workout.api.measurement.MeasurementType
import java.util.UUID

/** API representation of an exercise. `createdBy`/`createdAt` stay internal. */
data class ExerciseResponse(
    val id: UUID,
    val name: String,
    val category: String?,
    val measurementType: MeasurementType,
    val global: Boolean,
)

fun Exercise.toResponse(): ExerciseResponse =
    ExerciseResponse(
        id = id,
        name = name,
        category = category,
        measurementType = measurementType,
        global = createdBy == null,
    )
