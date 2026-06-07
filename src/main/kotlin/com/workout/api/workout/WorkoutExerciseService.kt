package com.workout.api.workout

import com.workout.api.common.NotFoundException
import com.workout.api.exercise.ExerciseService
import com.workout.api.workout.dto.AddWorkoutExerciseRequest
import com.workout.api.workout.dto.UpdateWorkoutExerciseRequest
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Collaborates with [WorkoutService] (parent ownership) and [ExerciseService] (the
 * referenced exercise must exist) so we return precise 404s instead of a DB FK error.
 */
@Service
class WorkoutExerciseService(
    private val repo: WorkoutExerciseRepository,
    private val workouts: WorkoutService,
    private val exercises: ExerciseService,
) {
    fun add(
        userId: UUID,
        workoutId: UUID,
        req: AddWorkoutExerciseRequest,
    ): WorkoutExercise {
        workouts.get(userId, workoutId) // 404 unless the workout is yours
        val exerciseId = requireNotNull(req.exerciseId) { "exerciseId is required" }
        exercises.byId(exerciseId) // 404 if the exercise doesn't exist
        return repo.insert(workoutId, exerciseId, req.position ?: 0, req.notes)
    }

    fun listFor(
        userId: UUID,
        workoutId: UUID,
    ): List<WorkoutExercise> {
        workouts.get(userId, workoutId) // 404 unless the workout is yours
        return repo.findAllForWorkout(workoutId)
    }

    fun update(
        userId: UUID,
        id: UUID,
        req: UpdateWorkoutExerciseRequest,
    ): WorkoutExercise {
        val existing = repo.findByIdForUser(id, userId) ?: throw NotFoundException("workout exercise $id not found")
        return repo.update(id, userId, req.position ?: existing.position, req.notes ?: existing.notes)
            ?: throw NotFoundException("workout exercise $id not found")
    }

    fun delete(
        userId: UUID,
        id: UUID,
    ) {
        if (!repo.deleteForUser(id, userId)) throw NotFoundException("workout exercise $id not found")
    }
}
