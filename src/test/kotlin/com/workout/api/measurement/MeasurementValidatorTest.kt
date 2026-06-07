package com.workout.api.measurement

import com.workout.api.common.ValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

/** Pure unit tests for the measurement matrix (no Spring). Mirrors the DB CHECK in schema.sql. */
class MeasurementValidatorTest {
    private fun bd(v: String) = BigDecimal(v)

    private fun validate(
        type: MeasurementType,
        m: SetMeasurement,
    ) = MeasurementValidator.validate(type, m)

    @Test
    fun `weight_reps accepts weight + reps`() {
        validate(MeasurementType.WEIGHT_REPS, SetMeasurement(weight = bd("100"), reps = 5))
    }

    @Test
    fun `weight_reps requires weight`() {
        assertThrows<ValidationException> { validate(MeasurementType.WEIGHT_REPS, SetMeasurement(reps = 5)) }
    }

    @Test
    fun `weight_reps rejects an extra field`() {
        assertThrows<ValidationException> {
            validate(MeasurementType.WEIGHT_REPS, SetMeasurement(weight = bd("100"), reps = 5, durationSeconds = 30))
        }
    }

    @Test
    fun `bodyweight accepts reps only`() {
        validate(MeasurementType.BODYWEIGHT, SetMeasurement(reps = 10))
    }

    @Test
    fun `bodyweight rejects weight`() {
        assertThrows<ValidationException> {
            validate(MeasurementType.BODYWEIGHT, SetMeasurement(reps = 10, weight = bd("5")))
        }
    }

    @Test
    fun `weighted_bodyweight accepts added_weight + reps`() {
        validate(MeasurementType.WEIGHTED_BODYWEIGHT, SetMeasurement(addedWeight = bd("20"), reps = 8))
    }

    @Test
    fun `duration accepts duration only`() {
        validate(MeasurementType.DURATION, SetMeasurement(durationSeconds = 60))
    }

    @Test
    fun `distance_time accepts distance, with optional duration`() {
        validate(MeasurementType.DISTANCE_TIME, SetMeasurement(distanceMeters = bd("5000")))
        validate(MeasurementType.DISTANCE_TIME, SetMeasurement(distanceMeters = bd("5000"), durationSeconds = 1500))
    }

    @Test
    fun `distance_time rejects reps`() {
        assertThrows<ValidationException> {
            validate(MeasurementType.DISTANCE_TIME, SetMeasurement(distanceMeters = bd("5000"), reps = 5))
        }
    }

    @Test
    fun `rpe out of range is rejected`() {
        assertThrows<ValidationException> {
            validate(MeasurementType.WEIGHT_REPS, SetMeasurement(weight = bd("100"), reps = 5, rpe = bd("11")))
        }
    }
}
