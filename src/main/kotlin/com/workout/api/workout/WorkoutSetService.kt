package com.workout.api.workout

import com.workout.api.common.NotFoundException
import com.workout.api.exercise.ExerciseService
import com.workout.api.measurement.MeasurementValidator
import com.workout.api.measurement.SetMeasurement
import com.workout.api.workout.dto.CreateSetRequest
import com.workout.api.workout.dto.UpdateSetRequest
import com.workout.api.workout.dto.toMeasurement
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class WorkoutSetService(
    private val sets: WorkoutSetRepository,
    private val workoutExercises: WorkoutExerciseService,
    private val exercises: ExerciseService,
) {
    @Transactional
    fun log(
        userId: UUID,
        workoutExerciseId: UUID,
        req: CreateSetRequest,
    ): WorkoutSet {
        val workoutExercise = workoutExercises.get(userId, workoutExerciseId) // 404 unless it's yours
        val exercise = exercises.byId(workoutExercise.exerciseId) // derive type from the exercise, not the client
        val measurement = req.toMeasurement()
        MeasurementValidator.validate(exercise.measurementType, measurement) // 400 on a bad combo
        return sets.insert(
            workoutExerciseId = workoutExerciseId,
            userId = userId,
            exerciseId = exercise.id,
            measurementType = exercise.measurementType,
            setIndex = req.setIndex ?: 0,
            performedAt = req.performedAt ?: OffsetDateTime.now(),
            m = measurement,
        )
    }

    fun listFor(
        userId: UUID,
        workoutExerciseId: UUID,
    ): List<WorkoutSet> {
        workoutExercises.get(userId, workoutExerciseId) // ownership
        return sets.findAllForWorkoutExercise(workoutExerciseId)
    }

    fun get(
        userId: UUID,
        id: UUID,
    ): WorkoutSet = sets.findByIdForUser(id, userId) ?: throw NotFoundException("set $id not found")

    @Transactional
    fun update(
        userId: UUID,
        id: UUID,
        req: UpdateSetRequest,
    ): WorkoutSet {
        val existing = get(userId, id) // ownership + 404
        val merged =
            SetMeasurement(
                weight = req.weight ?: existing.weight,
                reps = req.reps ?: existing.reps,
                addedWeight = req.addedWeight ?: existing.addedWeight,
                durationSeconds = req.durationSeconds ?: existing.durationSeconds,
                distanceMeters = req.distanceMeters ?: existing.distanceMeters,
                rpe = req.rpe ?: existing.rpe,
            )
        MeasurementValidator.validate(existing.measurementType, merged) // type is fixed by the exercise
        return sets.update(
            id = id,
            userId = userId,
            setIndex = req.setIndex ?: existing.setIndex,
            performedAt = req.performedAt ?: existing.performedAt,
            m = merged,
        ) ?: throw NotFoundException("set $id not found")
    }

    fun delete(
        userId: UUID,
        id: UUID,
    ) {
        if (!sets.deleteForUser(id, userId)) throw NotFoundException("set $id not found")
    }
}
