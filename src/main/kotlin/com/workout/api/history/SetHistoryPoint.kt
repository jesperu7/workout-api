package com.workout.api.history

import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * One point in an exercise's history — a read projection over `sets` (no internal ids),
 * safe to return directly. Which field matters depends on the query:
 * reps-at-a-weight, weight-at-a-rep-count, or everything over time.
 */
data class SetHistoryPoint(
    val performedAt: OffsetDateTime,
    val weight: BigDecimal?,
    val reps: Int?,
    val addedWeight: BigDecimal?,
    val durationSeconds: Int?,
    val distanceMeters: BigDecimal?,
    val rpe: BigDecimal?,
)
