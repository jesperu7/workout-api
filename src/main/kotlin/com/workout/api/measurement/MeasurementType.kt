package com.workout.api.measurement

/**
 * Mirrors the Postgres `measurement_type` enum (see schema.sql).
 *
 * DB values are lowercase (`weight_reps`); the Kotlin + JSON representation is the
 * enum name (`WEIGHT_REPS`). Conversion happens only at the DB boundary, via
 * [dbValue] / [fromDbValue], so the rest of the code deals in the enum.
 */
enum class MeasurementType {
    WEIGHT_REPS,
    BODYWEIGHT,
    WEIGHTED_BODYWEIGHT,
    DURATION,
    DISTANCE_TIME,
    ;

    /** Value as stored in the Postgres enum column (lowercase). */
    val dbValue: String get() = name.lowercase()

    companion object {
        fun fromDbValue(value: String): MeasurementType = valueOf(value.uppercase())
    }
}
