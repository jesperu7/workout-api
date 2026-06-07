package com.workout.api.workout

import com.workout.api.auth.userId
import com.workout.api.workout.dto.CreateSetRequest
import com.workout.api.workout.dto.SetResponse
import com.workout.api.workout.dto.UpdateSetRequest
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
class WorkoutSetController(
    private val service: WorkoutSetService,
) {
    @PostMapping("/workout-exercises/{workoutExerciseId}/sets")
    @ResponseStatus(HttpStatus.CREATED)
    fun log(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable workoutExerciseId: UUID,
        @Valid @RequestBody req: CreateSetRequest,
    ): SetResponse = service.log(jwt.userId, workoutExerciseId, req).toResponse()

    @GetMapping("/workout-exercises/{workoutExerciseId}/sets")
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable workoutExerciseId: UUID,
    ): List<SetResponse> = service.listFor(jwt.userId, workoutExerciseId).map { it.toResponse() }

    @PatchMapping("/sets/{id}")
    fun update(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateSetRequest,
    ): SetResponse = service.update(jwt.userId, id, req).toResponse()

    @DeleteMapping("/sets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ) = service.delete(jwt.userId, id)
}
