package com.workout.api.common

/** Thrown when a write collides with existing state (e.g. a duplicate exercise name); mapped to HTTP 409. */
class ConflictException(
    message: String,
) : RuntimeException(message)
