package com.workout.api.workout.dto

import com.workout.api.workout.Workout
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

/** Create a workout. `performedAt` defaults to now; both fields optional. */
data class CreateWorkoutRequest(
    val performedAt: OffsetDateTime? = null,
    @field:Size(max = 2000) val notes: String? = null,
)

/** Partial update: only non-null fields are applied (v1 can't clear notes back to null). */
data class UpdateWorkoutRequest(
    val performedAt: OffsetDateTime? = null,
    @field:Size(max = 2000) val notes: String? = null,
)

/** API view of a workout. `userId` is omitted — the caller always owns it. */
data class WorkoutResponse(
    val id: UUID,
    val performedAt: OffsetDateTime,
    val notes: String?,
    val programWorkoutId: UUID?,
    val createdAt: OffsetDateTime,
)

fun Workout.toResponse(): WorkoutResponse =
    WorkoutResponse(
        id = id,
        performedAt = performedAt,
        notes = notes,
        programWorkoutId = programWorkoutId,
        createdAt = createdAt,
    )
