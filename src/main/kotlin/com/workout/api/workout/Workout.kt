package com.workout.api.workout

import java.time.OffsetDateTime
import java.util.UUID

/** A training session that happened. `userId` is the owner (from the JWT, never the client). */
data class Workout(
    val id: UUID,
    val userId: UUID,
    val performedAt: OffsetDateTime,
    val notes: String?,
    val programWorkoutId: UUID?,
    val createdAt: OffsetDateTime,
)
