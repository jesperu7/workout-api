package com.workout.api.workout

import com.workout.api.common.NotFoundException
import com.workout.api.common.Page
import com.workout.api.workout.dto.CreateWorkoutRequest
import com.workout.api.workout.dto.UpdateWorkoutRequest
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Ownership lives here: every method takes the authenticated `userId` and only ever
 * touches that user's rows. A workout owned by someone else surfaces as 404 (not 403),
 * so we don't leak whether the id exists.
 */
@Service
class WorkoutService(
    private val workouts: WorkoutRepository,
) {
    fun create(
        userId: UUID,
        req: CreateWorkoutRequest,
    ): Workout = workouts.insert(userId, req.performedAt ?: OffsetDateTime.now(), req.notes)

    fun list(
        userId: UUID,
        limit: Int,
        offset: Int,
    ): Page<Workout> {
        val window = limit.coerceIn(1, 200)
        val start = offset.coerceAtLeast(0)
        return Page(workouts.findAllForUser(userId, window, start), window, start, workouts.countForUser(userId))
    }

    fun get(
        userId: UUID,
        id: UUID,
    ): Workout = workouts.findByIdForUser(id, userId) ?: throw NotFoundException("workout $id not found")

    fun update(
        userId: UUID,
        id: UUID,
        req: UpdateWorkoutRequest,
    ): Workout {
        val existing = get(userId, id) // ownership check + 404
        return workouts.update(
            id = id,
            userId = userId,
            performedAt = req.performedAt ?: existing.performedAt,
            notes = req.notes ?: existing.notes,
        ) ?: throw NotFoundException("workout $id not found")
    }

    fun delete(
        userId: UUID,
        id: UUID,
    ) {
        if (!workouts.deleteForUser(id, userId)) throw NotFoundException("workout $id not found")
    }
}
