package com.workout.api.workout

import com.workout.api.auth.userId
import com.workout.api.workout.dto.AddWorkoutExerciseRequest
import com.workout.api.workout.dto.UpdateWorkoutExerciseRequest
import com.workout.api.workout.dto.WorkoutExerciseResponse
import com.workout.api.workout.dto.toResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class WorkoutExerciseController(
    private val service: WorkoutExerciseService,
) {
    @PostMapping("/workouts/{workoutId}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable workoutId: UUID,
        @Valid @RequestBody req: AddWorkoutExerciseRequest,
    ): WorkoutExerciseResponse = service.add(jwt.userId, workoutId, req).toResponse()

    @GetMapping("/workouts/{workoutId}/exercises")
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable workoutId: UUID,
    ): List<WorkoutExerciseResponse> = service.listFor(jwt.userId, workoutId).map { it.toResponse() }

    @PatchMapping("/workout-exercises/{id}")
    fun update(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateWorkoutExerciseRequest,
    ): WorkoutExerciseResponse = service.update(jwt.userId, id, req).toResponse()

    @DeleteMapping("/workout-exercises/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ) = service.delete(jwt.userId, id)
}
