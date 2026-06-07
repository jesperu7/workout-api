package com.workout.api.workout.dto

import com.workout.api.workout.WorkoutExercise
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class AddWorkoutExerciseRequest(
    @field:NotNull val exerciseId: UUID?,
    val position: Int? = null,
    @field:Size(max = 2000) val notes: String? = null,
)

data class UpdateWorkoutExerciseRequest(
    val position: Int? = null,
    @field:Size(max = 2000) val notes: String? = null,
)

data class WorkoutExerciseResponse(
    val id: UUID,
    val workoutId: UUID,
    val exerciseId: UUID,
    val position: Int,
    val notes: String?,
)

fun WorkoutExercise.toResponse(): WorkoutExerciseResponse =
    WorkoutExerciseResponse(
        id = id,
        workoutId = workoutId,
        exerciseId = exerciseId,
        position = position,
        notes = notes,
    )
