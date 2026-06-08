package com.workout.api.workout.dto

import com.workout.api.measurement.MeasurementType
import com.workout.api.measurement.SetMeasurement
import com.workout.api.workout.WorkoutSet
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/** Log a set. Which measurement fields are required depends on the exercise's type (validated server-side). */
data class CreateSetRequest(
    @field:Min(0) val setIndex: Int? = null,
    val performedAt: OffsetDateTime? = null,
    @field:DecimalMin("0") val weight: BigDecimal? = null,
    @field:Min(0) val reps: Int? = null,
    @field:DecimalMin("0") val addedWeight: BigDecimal? = null,
    @field:Min(0) val durationSeconds: Int? = null,
    @field:DecimalMin("0") val distanceMeters: BigDecimal? = null,
    @field:DecimalMin("0") @field:DecimalMax("10") val rpe: BigDecimal? = null,
)

data class UpdateSetRequest(
    @field:Min(0) val setIndex: Int? = null,
    val performedAt: OffsetDateTime? = null,
    @field:DecimalMin("0") val weight: BigDecimal? = null,
    @field:Min(0) val reps: Int? = null,
    @field:DecimalMin("0") val addedWeight: BigDecimal? = null,
    @field:Min(0) val durationSeconds: Int? = null,
    @field:DecimalMin("0") val distanceMeters: BigDecimal? = null,
    @field:DecimalMin("0") @field:DecimalMax("10") val rpe: BigDecimal? = null,
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
