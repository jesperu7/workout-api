package com.workout.api.history

import com.workout.api.auth.userId
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

/**
 * Bidirectional history for one exercise (the headline feature):
 *   GET /api/exercises/{id}/history?weight=100  -> reps at that weight over time
 *   GET /api/exercises/{id}/history?reps=5       -> weight at that rep count over time
 *   GET /api/exercises/{id}/history              -> all sets over time
 */
@RestController
@RequestMapping("/api/exercises/{exerciseId}/history")
class HistoryController(
    private val service: HistoryService,
) {
    @GetMapping
    fun history(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable exerciseId: UUID,
        @RequestParam(required = false) weight: BigDecimal?,
        @RequestParam(required = false) reps: Int?,
    ): List<SetHistoryPoint> = service.forExercise(jwt.userId, exerciseId, weight, reps)
}
