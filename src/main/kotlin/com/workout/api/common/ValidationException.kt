package com.workout.api.common

/** Thrown when a request is semantically invalid (e.g. a bad measurement combo); mapped to 400. */
class ValidationException(
    message: String,
) : RuntimeException(message)
