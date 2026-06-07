package com.workout.api.workout

import java.util.UUID

/** "In this workout I did this exercise." Owned (transitively) via its workout. */
data class WorkoutExercise(
    val id: UUID,
    val workoutId: UUID,
    val exerciseId: UUID,
    val position: Int,
    val notes: String?,
)
