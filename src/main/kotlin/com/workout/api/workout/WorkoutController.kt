package com.workout.api.workout

import com.workout.api.auth.userId
import com.workout.api.common.Page
import com.workout.api.workout.dto.CreateWorkoutRequest
import com.workout.api.workout.dto.UpdateWorkoutRequest
import com.workout.api.workout.dto.WorkoutResponse
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/workouts")
class WorkoutController(
    private val workouts: WorkoutService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody req: CreateWorkoutRequest,
    ): WorkoutResponse = workouts.create(jwt.userId, req).toResponse()

    @GetMapping
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) name: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): Page<WorkoutResponse> = workouts.list(jwt.userId, name, limit, offset).map { it.toResponse() }

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ): WorkoutResponse = workouts.get(jwt.userId, id).toResponse()

    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateWorkoutRequest,
    ): WorkoutResponse = workouts.update(jwt.userId, id, req).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ) = workouts.delete(jwt.userId, id)
}
