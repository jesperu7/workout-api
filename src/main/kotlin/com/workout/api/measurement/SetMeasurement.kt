package com.workout.api.measurement

import java.math.BigDecimal

/** The measurement payload of a set — mirrors the nullable measurement columns on `sets`. */
data class SetMeasurement(
    val weight: BigDecimal? = null,
    val reps: Int? = null,
    val addedWeight: BigDecimal? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: BigDecimal? = null,
    val rpe: BigDecimal? = null,
)
