package com.neogenesis.server.application.regenops

import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.persistence.DatabaseFactory
import com.neogenesis.server.infrastructure.persistence.JdbcRegenOpsStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegenOpsEvidenceIntegrityTest {
    @Test
    fun `detects evidence chain tampering`() {
        val dataSource =
            DatabaseFactory(
                AppConfig.DatabaseConfig(
                    jdbcUrl = "jdbc:h2:mem:regenops-evidence-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
            val service = RegenOpsService(JdbcRegenOpsStore(dataSource))
            service.createDraft(
                tenantId = "tenant-a",
                protocolId = "protocol-1",
                title = "Protocol 1",
                contentJson = "{\"steps\":[\"a\"]}",
                actorId = "user-1",
            )
            service.publishVersion(
                tenantId = "tenant-a",
                protocolId = "protocol-1",
                actorId = "user-1",
                changelog = "first",
            )
            service.startRun(
                tenantId = "tenant-a",
                protocolId = "protocol-1",
                protocolVersion = 1,
                runId = "run-1",
                gatewayId = "gw-1",
                actorId = "user-1",
            )
            service.pauseRun(
                tenantId = "tenant-a",
                runId = "run-1",
                actorId = "user-1",
                reason = "hold",
            )
            service.abortRun(
                tenantId = "tenant-a",
                runId = "run-1",
                actorId = "user-1",
                reason = "stop",
            )

            val beforeTamper = service.verifyEvidenceChain("tenant-a")
            assertTrue(beforeTamper.valid)

            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        "UPDATE regen_evidence_events SET payload_json='{\"tampered\":true}' " +
                            "WHERE id = (SELECT MIN(id) FROM regen_evidence_events WHERE tenant_id='tenant-a')",
                    )
                }
            }

            val afterTamper = service.verifyEvidenceChain("tenant-a")
            assertFalse(afterTamper.valid)
        } finally {
            if (dataSource is AutoCloseable) {
                dataSource.close()
            }
        }
    }
}
