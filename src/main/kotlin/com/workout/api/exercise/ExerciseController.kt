package com.workout.api.exercise

import com.workout.api.common.Page
import com.workout.api.exercise.dto.ExerciseResponse
import com.workout.api.exercise.dto.toResponse
import com.workout.api.measurement.MeasurementType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/exercises")
class ExerciseController(
    private val exercises: ExerciseService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) measurementType: MeasurementType?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): Page<ExerciseResponse> = exercises.list(category, measurementType, limit, offset).map { it.toResponse() }

    @GetMapping("/{id}")
    fun byId(
        @PathVariable id: UUID,
    ): ExerciseResponse = exercises.byId(id).toResponse()
}
