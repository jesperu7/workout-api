package com.workout.api.workout.dto

import com.workout.api.workout.Workout
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

/** Create a workout. `performedAt` defaults to now; all fields optional. `name` is an optional label ("Push Day"). */
data class CreateWorkoutRequest(
    val performedAt: OffsetDateTime? = null,
    @field:Size(max = 200) val name: String? = null,
    @field:Size(max = 2000) val notes: String? = null,
)

/**
 * Partial update: only fields you send are applied (absent/null = unchanged).
 * `notes` is set-only (v1 can't clear it back to null); `name` is trimmed, and sending a
 * blank value clears it to null.
 */
data class UpdateWorkoutRequest(
    val performedAt: OffsetDateTime? = null,
    @field:Size(max = 200) val name: String? = null,
    @field:Size(max = 2000) val notes: String? = null,
)

/** API view of a workout. `userId` is omitted — the caller always owns it. */
data class WorkoutResponse(
    val id: UUID,
    val performedAt: OffsetDateTime,
    val name: String?,
    val notes: String?,
    val programWorkoutId: UUID?,
    val createdAt: OffsetDateTime,
)

fun Workout.toResponse(): WorkoutResponse =
    WorkoutResponse(
        id = id,
        performedAt = performedAt,
        name = name,
        notes = notes,
        programWorkoutId = programWorkoutId,
        createdAt = createdAt,
    )
