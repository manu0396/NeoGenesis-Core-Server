package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.infrastructure.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseFactoryMigrationTest {
    @Test
    fun `applies flyway migrations on clean database`() {
        val jdbcUrl = "jdbc:h2:mem:neogenesis-migrate-clean-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        val dataSource = createDataSource(jdbcUrl)

        try {
            val appliedRows = flywayRows(dataSource)
            val outboxColumns = integrationOutboxColumns(dataSource)

            assertTrue(appliedRows > 0, "Expected Flyway history rows after migration")
            assertTrue("status" in outboxColumns, "Expected integration_outbox.status column")
            assertTrue("processing_started_at" in outboxColumns, "Expected integration_outbox.processing_started_at column")
        } finally {
            if (dataSource is AutoCloseable) {
                dataSource.close()
            }
        }
    }

    @Test
    fun `re-running initialization on migrated schema is idempotent`() {
        val jdbcUrl = "jdbc:h2:mem:neogenesis-migrate-reapply-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        val firstDataSource = createDataSource(jdbcUrl)
        val firstFlywayRows =
            try {
                flywayRows(firstDataSource)
            } finally {
                if (firstDataSource is AutoCloseable) {
                    firstDataSource.close()
                }
            }

        val secondDataSource = createDataSource(jdbcUrl)
        try {
            val secondFlywayRows = flywayRows(secondDataSource)
            assertEquals(firstFlywayRows, secondFlywayRows)
        } finally {
            if (secondDataSource is AutoCloseable) {
                secondDataSource.close()
            }
        }
    }

    private fun createDataSource(jdbcUrl: String): javax.sql.DataSource {
        return DatabaseFactory(
            AppConfig.DatabaseConfig(
                jdbcUrl = jdbcUrl,
                username = "sa",
                password = "",
                maximumPoolSize = 2,
                migrateOnStartup = true,
                connectionTimeoutMs = 3_000,
                validationTimeoutMs = 1_000,
                idleTimeoutMs = 600_000,
                maxLifetimeMs = 1_800_000,
            ),
        ).initialize()
    }

    private fun flywayRows(dataSource: javax.sql.DataSource): Int {
        return dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
    }

    private fun integrationOutboxColumns(dataSource: javax.sql.DataSource): Set<String> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT COLUMN_NAME
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'integration_outbox'
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    buildSet {
                        while (rs.next()) {
                            add(rs.getString("COLUMN_NAME").lowercase())
                        }
                    }
                }
            }
        }
    }
}
