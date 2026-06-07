package com.workout.api.common

/** Thrown by services when a requested resource doesn't exist; mapped to HTTP 404. */
class NotFoundException(
    message: String,
) : RuntimeException(message)
