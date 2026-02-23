package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.infrastructure.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JdbcOutboxEventStoreClaimTest {
    @Test
    fun `claims pending events in batches without duplicates`() {
        val dataSource =
            DatabaseFactory(
                AppConfig.DatabaseConfig(
                    jdbcUrl = "jdbc:h2:mem:neogenesis-claim-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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

        try {
            val store = JdbcOutboxEventStore(dataSource)
            store.enqueue("FHIR_INGESTED", "patient-1", """{"event":"1"}""")
            store.enqueue("HL7_INGESTED", "patient-1", """{"event":"2"}""")

            val first = store.claimPending(limit = 1, processingTtlMs = 60_000)
            val second = store.claimPending(limit = 1, processingTtlMs = 60_000)

            assertEquals(1, first.size)
            assertEquals(1, second.size)
            assertTrue(first.first().id != second.first().id, "claimPending must not duplicate claimed IDs")
        } finally {
            if (dataSource is AutoCloseable) {
                dataSource.close()
            }
        }
    }
}
