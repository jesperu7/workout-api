package com.workout.api.db

import com.workout.api.TestcontainersConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * Guards the V4 RLS lockout against real Postgres: every user-data table must have RLS
 * ENABLED and NO policies, so the `anon`/`authenticated` roles PostgREST uses (the
 * client's public anon key) are denied all direct access. The backend bypasses RLS as
 * the table owner, so it is unaffected. A future migration that disables RLS, adds a
 * permissive policy, or introduces a data table without locking it down will fail this
 * test. (See V4__rls_lockout.sql.)
 *
 * Note: `flyway_schema_history` is intentionally NOT locked here — it holds migration
 * metadata, not user data, and a Flyway migration can't ENABLE RLS on it without
 * self-deadlocking (Flyway holds that table while it migrates). Lock it manually in the
 * cloud SQL editor if a clean advisor report is wanted. v1.5/v2 add their data tables here.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class RlsLockoutTest {
    @Autowired
    lateinit var jdbc: JdbcClient

    private val lockedTables = listOf("exercises", "workouts", "workout_exercises", "sets")

    @Test
    fun `RLS is enabled on every user-data table`() {
        lockedTables.forEach { table ->
            // explicit type coerces the JDBC platform type to a non-null Kotlin Boolean,
            // so the assertion call resolves cleanly (and null DB results fail fast)
            val enabled: Boolean =
                jdbc
                    .sql(
                        """
                        SELECT c.relrowsecurity
                        FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = 'public' AND c.relname = :table
                        """.trimIndent(),
                    ).param("table", table)
                    .query(Boolean::class.javaObjectType)
                    .single()
            assertTrue(enabled, "RLS must be enabled on $table")
        }
    }

    @Test
    fun `locked-down tables have no policies (no direct client access)`() {
        lockedTables.forEach { table ->
            val policyCount: Long =
                jdbc
                    .sql("SELECT count(*) FROM pg_policies WHERE schemaname = 'public' AND tablename = :table")
                    .param("table", table)
                    .query(Long::class.javaObjectType)
                    .single()
            assertEquals(0L, policyCount, "$table must have no RLS policies (lockout)")
        }
    }
}
