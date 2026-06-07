package com.workout.api.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Executable architecture rules — the conventions in CLAUDE.md and .claude/rules/
 * enforced as tests, so violations fail the build instead of relying on review.
 *
 * These run without Spring/Docker (no @SpringBootTest). Some rules pass vacuously
 * until the feature packages exist; richer same-package dependency checks
 * (e.g. a controller holding a repository field) will be added at M3+ once there
 * are real classes to assert against.
 */
class ArchitectureTest {
    @Test
    fun `constructor injection only — no field injection`() {
        Konsist
            .scopeFromProject()
            .classes()
            .assertFalse { klass -> klass.properties().any { it.hasAnnotationOf(listOf(Autowired::class)) } }
    }

    @Test
    fun `no wildcard imports`() {
        Konsist
            .scopeFromProject()
            .imports
            .assertFalse { it.isWildcard }
    }

    @Test
    fun `only repositories access the database via JdbcClient`() {
        Konsist
            .scopeFromProject()
            .files
            .assertTrue { file ->
                val usesJdbcClient =
                    file.hasImport { it.name == "org.springframework.jdbc.core.simple.JdbcClient" }
                !usesJdbcClient || file.path.endsWith("Repository.kt")
            }
    }

    @Test
    fun `services do not depend on web or servlet types`() {
        Konsist
            .scopeFromProject()
            .files
            .assertTrue { file ->
                !file.path.endsWith("Service.kt") ||
                    !file.hasImport {
                        it.name.startsWith("org.springframework.http") ||
                            it.name.startsWith("jakarta.servlet")
                    }
            }
    }

    @Test
    fun `controllers, services and repositories reside under the api package`() {
        Konsist
            .scopeFromProject()
            .classes()
            .assertTrue { klass ->
                val isLayerClass = listOf("Controller", "Service", "Repository").any { klass.name.endsWith(it) }
                !isLayerClass || klass.resideInPackage("com.workout.api..")
            }
    }
}
