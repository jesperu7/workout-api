package com.workout.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WorkoutApiApplication

fun main(args: Array<String>) {
	runApplication<WorkoutApiApplication>(*args)
}
