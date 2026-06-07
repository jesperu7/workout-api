package com.workout.api.workout.dto

import com.workout.api.measurement.MeasurementType
import com.workout.api.measurement.SetMeasurement
import com.workout.api.workout.WorkoutSet
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/** Log a set. Which measurement fields are required depends on the exercise's type (validated server-side). */
data class CreateSetRequest(
    val setIndex: Int? = null,
    val performedAt: OffsetDateTime? = null,
    val weight: BigDecimal? = null,
    val reps: Int? = null,
    val addedWeight: BigDecimal? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: BigDecimal? = null,
    val rpe: BigDecimal? = null,
)

data class UpdateSetRequest(
    val setIndex: Int? = null,
    val performedAt: OffsetDateTime? = null,
    val weight: BigDecimal? = null,
    val reps: Int? = null,
    val addedWeight: BigDecimal? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: BigDecimal? = null,
    val rpe: BigDecimal? = null,
)

/** `userId` and `programSetId` stay internal. `measurementType` is echoed for client convenience. */
data class SetResponse(
    val id: UUID,
    val workoutExerciseId: UUID,
    val exerciseId: UUID,
    val measurementType: MeasurementType,
    val setIndex: Int,
    val performedAt: OffsetDateTime,
    val weight: BigDecimal?,
    val reps: Int?,
    val addedWeight: BigDecimal?,
    val durationSeconds: Int?,
    val distanceMeters: BigDecimal?,
    val rpe: BigDecimal?,
)

fun CreateSetRequest.toMeasurement(): SetMeasurement = SetMeasurement(weight, reps, addedWeight, durationSeconds, distanceMeters, rpe)

fun WorkoutSet.toResponse(): SetResponse =
    SetResponse(
        id = id,
        workoutExerciseId = workoutExerciseId,
        exerciseId = exerciseId,
        measurementType = measurementType,
        setIndex = setIndex,
        performedAt = performedAt,
        weight = weight,
        reps = reps,
        addedWeight = addedWeight,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        rpe = rpe,
    )
