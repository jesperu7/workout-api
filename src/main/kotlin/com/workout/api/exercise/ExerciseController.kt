package com.workout.api.exercise

import com.workout.api.auth.userId
import com.workout.api.common.Page
import com.workout.api.exercise.dto.CreateExerciseRequest
import com.workout.api.exercise.dto.ExerciseResponse
import com.workout.api.exercise.dto.toResponse
import com.workout.api.measurement.MeasurementType
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/exercises")
class ExerciseController(
    private val exercises: ExerciseService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody req: CreateExerciseRequest,
    ): ExerciseResponse = exercises.create(jwt.userId, req).toResponse()

    @GetMapping
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) measurementType: MeasurementType?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): Page<ExerciseResponse> =
        exercises
            .list(jwt.userId, category, measurementType, name, limit, offset)
            .map { it.toResponse() }

    @GetMapping("/{id}")
    fun byId(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ): ExerciseResponse = exercises.byId(jwt.userId, id).toResponse()
}
