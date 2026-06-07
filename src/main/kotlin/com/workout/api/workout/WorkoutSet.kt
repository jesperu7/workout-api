package com.workout.api.workout

import com.workout.api.measurement.MeasurementType
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/**
 * One performed set. `userId`, `exerciseId`, `measurementType` are denormalized (derived from the
 * parent chain / exercise) — the app sets them, never the client. Weights are `BigDecimal` (kg).
 */
data class WorkoutSet(
    val id: UUID,
    val workoutExerciseId: UUID,
    val userId: UUID,
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
    val programSetId: UUID?,
)
