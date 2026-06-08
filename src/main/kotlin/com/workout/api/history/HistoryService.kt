package com.workout.api.history

import com.workout.api.exercise.ExerciseService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class HistoryService(
    private val history: HistoryRepository,
    private val exercises: ExerciseService,
) {
    fun forExercise(
        userId: UUID,
        exerciseId: UUID,
        weight: BigDecimal?,
        reps: Int?,
    ): List<SetHistoryPoint> {
        exercises.byId(exerciseId) // 404 if the exercise doesn't exist
        return history.forExercise(userId, exerciseId, weight, reps)
    }
}
