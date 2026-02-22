package com.neogenesis.server.infrastructure.grpc

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.neogenesis.grpc.BioPrintServiceGrpcKt
import com.neogenesis.grpc.PrinterTelemetry
import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.ControlDecisionService
import com.neogenesis.server.application.InMemoryTelemetrySnapshotService
import com.neogenesis.server.application.sre.LatencyBudgetService
import com.neogenesis.server.application.telemetry.AdvancedBioSimulationService
import com.neogenesis.server.application.telemetry.ClosedLoopControlService
import com.neogenesis.server.application.telemetry.TelemetryProcessingService
import com.neogenesis.server.application.twin.DigitalTwinService
import com.neogenesis.server.domain.policy.DefaultTelemetrySafetyPolicy
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import com.neogenesis.server.infrastructure.persistence.DatabaseFactory
import com.neogenesis.server.infrastructure.persistence.JdbcAuditEventStore
import com.neogenesis.server.infrastructure.persistence.JdbcControlCommandStore
import com.neogenesis.server.infrastructure.persistence.JdbcDigitalTwinStore
import com.neogenesis.server.infrastructure.persistence.JdbcLatencyBreachStore
import com.neogenesis.server.infrastructure.persistence.JdbcPrintSessionStore
import com.neogenesis.server.infrastructure.persistence.JdbcRetinalPlanStore
import com.neogenesis.server.infrastructure.persistence.JdbcTelemetryEventStore
import io.grpc.ClientInterceptors
import io.grpc.Metadata
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.stub.MetadataUtils
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GrpcTelemetryIntegrationTest {

    @Test
    fun `rejects grpc telemetry stream without bearer token`() {
        val fixture = fixture()
        try {
            val exception = assertFailsWith<StatusException> {
                runBlocking {
                    fixture.stubWithToken(null)
                        .streamTelemetryAndControl(flowOf(sampleTelemetry()))
                        .first()
                }
            }

            assertEquals(io.grpc.Status.Code.UNAUTHENTICATED, exception.status.code)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `persists telemetry and command over grpc stream with authorized role`() {
        val fixture = fixture()
        try {
            val command = runBlocking {
                fixture.stubWithToken(fixture.issueToken(listOf("controller")))
                    .streamTelemetryAndControl(flowOf(sampleTelemetry()))
                    .first()
            }

            assertTrue(command.commandId.isNotBlank())
            assertTrue(command.actionType.isNotBlank())

            val telemetryPersisted = fixture.telemetryEventStore.recent(5)
            val commandsPersisted = fixture.commandStore.recent(5)
            assertEquals(1, telemetryPersisted.size)
            assertEquals(1, commandsPersisted.size)
            assertEquals("printer-1", telemetryPersisted.first().telemetry.printerId)
        } finally {
            fixture.close()
        }
    }

    private fun fixture(): TestFixture {
        val dataSource = DatabaseFactory(
            AppConfig.DatabaseConfig(
                jdbcUrl = "jdbc:h2:mem:neogenesis-grpc-integration-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                username = "sa",
                password = "",
                maximumPoolSize = 4,
                migrateOnStartup = true
            )
        ).initialize()

        val meterRegistry = SimpleMeterRegistry()
        val metricsService = OperationalMetricsService(meterRegistry)
        val auditTrailService = AuditTrailService(JdbcAuditEventStore(dataSource), metricsService)
        val telemetryEventStore = JdbcTelemetryEventStore(dataSource)
        val commandStore = JdbcControlCommandStore(dataSource)
        val telemetryProcessingService = TelemetryProcessingService(
            closedLoopControlService = ClosedLoopControlService(
                decisionService = ControlDecisionService(DefaultTelemetrySafetyPolicy()),
                printSessionStore = JdbcPrintSessionStore(dataSource),
                retinalPlanStore = JdbcRetinalPlanStore(dataSource)
            ),
            advancedBioSimulationService = AdvancedBioSimulationService(),
            telemetrySnapshotService = InMemoryTelemetrySnapshotService(),
            telemetryEventStore = telemetryEventStore,
            controlCommandStore = commandStore,
            digitalTwinService = DigitalTwinService(JdbcDigitalTwinStore(dataSource)),
            auditTrailService = auditTrailService,
            metricsService = metricsService,
            latencyBudgetService = LatencyBudgetService(
                thresholdMs = 50,
                latencyBreachStore = JdbcLatencyBreachStore(dataSource),
                auditTrailService = auditTrailService,
                metricsService = metricsService
            )
        )

        val verifier = JWT.require(Algorithm.HMAC256(TEST_SECRET))
            .withIssuer(TEST_ISSUER)
            .withAudience(TEST_AUDIENCE)
            .build()

        val serviceDefinition = io.grpc.ServerInterceptors.intercept(
            BioPrintGrpcService(telemetryProcessingService).bindService(),
            GrpcJwtAuthInterceptor(verifier)
        )

        val serverName = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(serviceDefinition)
            .addService(ProtoReflectionService.newInstance())
            .build()
            .start()
        val channel = InProcessChannelBuilder.forName(serverName)
            .directExecutor()
            .build()

        return TestFixture(
            dataSource = dataSource,
            server = server,
            channel = channel,
            telemetryEventStore = telemetryEventStore,
            commandStore = commandStore
        )
    }

    private fun sampleTelemetry(): PrinterTelemetry {
        return PrinterTelemetry.newBuilder()
            .setPrinterId("printer-1")
            .setTimestampMs(System.currentTimeMillis())
            .setNozzleTempCelsius(37.1f)
            .setExtrusionPressureKpa(109.4f)
            .setCellViabilityIndex(0.95f)
            .setBioInkViscosityIndex(0.82f)
            .setBioInkPh(7.35f)
            .setNirIiTempCelsius(37.0f)
            .setMorphologicalDefectProbability(0.04f)
            .setPrintJobId("job-1")
            .setTissueType("retina")
            .build()
    }

    private class TestFixture(
        private val dataSource: javax.sql.DataSource,
        private val server: io.grpc.Server,
        private val channel: io.grpc.ManagedChannel,
        val telemetryEventStore: JdbcTelemetryEventStore,
        val commandStore: JdbcControlCommandStore
    ) : AutoCloseable {

        fun issueToken(roles: List<String>): String {
            return JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject("grpc-client")
                .withClaim("roles", roles)
                .sign(Algorithm.HMAC256(TEST_SECRET))
        }

        fun stubWithToken(token: String?): BioPrintServiceGrpcKt.BioPrintServiceCoroutineStub {
            if (token.isNullOrBlank()) {
                return BioPrintServiceGrpcKt.BioPrintServiceCoroutineStub(channel)
            }
            val metadata = Metadata().apply {
                put(AUTHORIZATION_HEADER, "Bearer $token")
            }
            val intercepted = ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(metadata))
            return BioPrintServiceGrpcKt.BioPrintServiceCoroutineStub(intercepted)
        }

        override fun close() {
            channel.shutdownNow()
            server.shutdownNow()
            if (dataSource is AutoCloseable) {
                dataSource.close()
            }
        }
    }

    companion object {
        private const val TEST_ISSUER = "neogenesis-auth"
        private const val TEST_AUDIENCE = "neogenesis-api"
        private const val TEST_SECRET = "integration-test-secret-with-at-least-32-chars"
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
