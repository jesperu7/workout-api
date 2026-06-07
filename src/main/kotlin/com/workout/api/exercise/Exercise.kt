package com.workout.api.exercise

import com.workout.api.measurement.MeasurementType
import java.time.OffsetDateTime
import java.util.UUID

/** Domain model for a catalog exercise (DB-shaped). Not exposed directly — see dto/. */
data class Exercise(
    val id: UUID,
    val name: String,
    val category: String?,
    val measurementType: MeasurementType,
    val createdBy: UUID?,
    val createdAt: OffsetDateTime,
)
