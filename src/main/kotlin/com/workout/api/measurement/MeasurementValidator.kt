package com.workout.api.measurement

import com.workout.api.common.ValidationException
import java.math.BigDecimal

/**
 * Validates a set's measurement columns against its [MeasurementType] — the same matrix as the
 * DB CHECK in schema.sql, but in code so a bad combo is a clear 400 (the DB CHECK is the backstop).
 * Reused by `sets` now and `program_sets` in v1.5.
 */
object MeasurementValidator {
    fun validate(
        type: MeasurementType,
        m: SetMeasurement,
    ) {
        m.rpe?.let { rpe ->
            if (rpe < BigDecimal.ZERO || rpe > BigDecimal.TEN) {
                throw ValidationException("rpe must be between 0 and 10")
            }
        }

        val present =
            mapOf(
                "weight" to (m.weight != null),
                "reps" to (m.reps != null),
                "addedWeight" to (m.addedWeight != null),
                "durationSeconds" to (m.durationSeconds != null),
                "distanceMeters" to (m.distanceMeters != null),
            )

        // (required fields, also-allowed-but-optional fields) per type. Everything else is forbidden.
        val rule: Pair<List<String>, List<String>> =
            when (type) {
                MeasurementType.WEIGHT_REPS -> listOf("weight", "reps") to emptyList()
                MeasurementType.BODYWEIGHT -> listOf("reps") to emptyList()
                MeasurementType.WEIGHTED_BODYWEIGHT -> listOf("addedWeight", "reps") to emptyList()
                MeasurementType.DURATION -> listOf("durationSeconds") to emptyList()
                MeasurementType.DISTANCE_TIME -> listOf("distanceMeters") to listOf("durationSeconds")
            }
        val (required, optional) = rule

        required.forEach { field ->
            if (present[field] != true) throw ValidationException("$type requires $field")
        }
        present.forEach { (field, isPresent) ->
            if (isPresent && field !in required && field !in optional) {
                throw ValidationException("$type does not allow $field")
            }
        }
    }
}
