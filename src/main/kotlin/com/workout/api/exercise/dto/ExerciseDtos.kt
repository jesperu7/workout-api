package com.workout.api.exercise.dto

import com.workout.api.exercise.Exercise
import com.workout.api.measurement.MeasurementType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * Create a user-owned exercise. The owner is always the authenticated user (JWT),
 * never taken from the body. The client picks the measurement type once, here —
 * it then drives which set columns are required when logging.
 */
data class CreateExerciseRequest(
    @field:NotBlank @field:Size(max = 200) val name: String?,
    @field:Size(max = 100) val category: String? = null,
    @field:NotNull val measurementType: MeasurementType?,
)

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
