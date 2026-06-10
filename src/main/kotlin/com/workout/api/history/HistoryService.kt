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
        exercises.byId(userId, exerciseId) // 404 unless it's visible to you (global or yours)
        return history.forExercise(userId, exerciseId, weight, reps)
    }
}
