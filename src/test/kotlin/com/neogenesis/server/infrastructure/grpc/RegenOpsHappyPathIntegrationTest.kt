package com.neogenesis.server.infrastructure.grpc

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.neogenesis.grpc.CreateDraftRequest
import com.neogenesis.grpc.ExportRunReportRequest
import com.neogenesis.grpc.GatewayRunEvent
import com.neogenesis.grpc.GatewayTelemetry
import com.neogenesis.grpc.GetRunRequest
import com.neogenesis.grpc.GetReproducibilityScoreRequest
import com.neogenesis.grpc.GatewayServiceGrpcKt
import com.neogenesis.grpc.MetricsServiceGrpcKt
import com.neogenesis.grpc.ProtocolServiceGrpcKt
import com.neogenesis.grpc.PublishVersionRequest
import com.neogenesis.grpc.PushRunEventsRequest
import com.neogenesis.grpc.PushTelemetryRequest
import com.neogenesis.grpc.RegisterGatewayRequest
import com.neogenesis.grpc.RunServiceGrpcKt
import com.neogenesis.grpc.StartRunRequest
import com.neogenesis.grpc.StreamRunEventsRequest
import com.neogenesis.server.application.regenops.RegenOpsService
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.grpc.regenops.RegenGatewayGrpcService
import com.neogenesis.server.infrastructure.grpc.regenops.RegenMetricsGrpcService
import com.neogenesis.server.infrastructure.grpc.regenops.RegenProtocolGrpcService
import com.neogenesis.server.infrastructure.grpc.regenops.RegenRunGrpcService
import com.neogenesis.server.infrastructure.persistence.DatabaseFactory
import com.neogenesis.server.infrastructure.persistence.JdbcRegenOpsStore
import io.grpc.ClientInterceptors
import io.grpc.Metadata
import io.grpc.ServerInterceptors
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegenOpsHappyPathIntegrationTest {
    @Test
    fun `runs protocol to report happy path`() {
        val dataSource =
            DatabaseFactory(
                AppConfig.DatabaseConfig(
                    jdbcUrl = "jdbc:h2:mem:regenops-happy-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                    username = "sa",
                    password = "",
                    maximumPoolSize = 4,
                    migrateOnStartup = true,
                    connectionTimeoutMs = 3_000,
                    validationTimeoutMs = 1_000,
                    idleTimeoutMs = 600_000,
                    maxLifetimeMs = 1_800_000,
                ),
            ).initialize()

        val regenOpsService = RegenOpsService(JdbcRegenOpsStore(dataSource))
        val verifier =
            JWT.require(Algorithm.HMAC256(TEST_SECRET))
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .build()

        val authInterceptor = GrpcJwtAuthInterceptor(verifier)

        val serverName = InProcessServerBuilder.generateName()
        val server =
            InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(RegenProtocolGrpcService(regenOpsService), authInterceptor))
                .addService(ServerInterceptors.intercept(RegenRunGrpcService(regenOpsService), authInterceptor))
                .addService(ServerInterceptors.intercept(RegenGatewayGrpcService(regenOpsService), authInterceptor))
                .addService(ServerInterceptors.intercept(RegenMetricsGrpcService(regenOpsService), authInterceptor))
                .build()
                .start()

        val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()

        try {
            val token = issueToken(role = "regenops_operator", tenantId = "tenant-a")
            val protocolStub = ProtocolServiceGrpcKt.ProtocolServiceCoroutineStub(attachToken(channel, token))
            val runStub = RunServiceGrpcKt.RunServiceCoroutineStub(attachToken(channel, token))
            val gatewayStub = GatewayServiceGrpcKt.GatewayServiceCoroutineStub(attachToken(channel, token))
            val metricsStub = MetricsServiceGrpcKt.MetricsServiceCoroutineStub(attachToken(channel, token))

            runBlocking {
                gatewayStub.registerGateway(
                    RegisterGatewayRequest.newBuilder()
                        .setTenantId("tenant-a")
                        .setGatewayId("gw-1")
                        .setDisplayName("gateway-1")
                        .setCertificateSerial("cert-1")
                        .build(),
                )

                val draft =
                    protocolStub.createDraft(
                        CreateDraftRequest.newBuilder()
                            .setTenantId("tenant-a")
                            .setProtocolId("protocol-alpha")
                            .setTitle("Protocol Alpha")
                            .setContentJson("{\"steps\":[\"seed\",\"grow\"]}")
                            .setActorId("user-1")
                            .build(),
                    )
                assertEquals("protocol-alpha", draft.protocolId)

                val published =
                    protocolStub.publishVersion(
                        PublishVersionRequest.newBuilder()
                            .setTenantId("tenant-a")
                            .setProtocolId("protocol-alpha")
                            .setActorId("user-1")
                            .setChangelog("initial")
                            .build(),
                    )
                assertEquals(1, published.version)

                val started =
                    runStub.startRun(
                        StartRunRequest.newBuilder()
                            .setTenantId("tenant-a")
                            .setProtocolId("protocol-alpha")
                            .setProtocolVersion(1)
                            .setRunId("run-1")
                            .setGatewayId("gw-1")
                            .setActorId("user-1")
                            .build(),
                    )
                assertEquals("RUNNING", started.status)

                gatewayStub.pushRunEvents(
                    PushRunEventsRequest.newBuilder()
                        .setTenantId("tenant-a")
                        .setGatewayId("gw-1")
                        .addEvents(
                            GatewayRunEvent.newBuilder()
                                .setRunId("run-1")
                                .setEventType("gateway.phase.entered")
                                .setPayloadJson("{\"phase\":\"incubation\"}")
                                .setCreatedAtMs(System.currentTimeMillis())
                                .build(),
                        ).build(),
                )

                gatewayStub.pushTelemetry(
                    PushTelemetryRequest.newBuilder()
                        .setTenantId("tenant-a")
                        .setGatewayId("gw-1")
                        .addTelemetry(
                            GatewayTelemetry.newBuilder()
                                .setRunId("run-1")
                                .setMetricKey("viability")
                                .setMetricValue(0.95)
                                .setUnit("ratio")
                                .setDriftScore(0.05)
                                .setRecordedAtMs(System.currentTimeMillis())
                                .build(),
                        ).build(),
                )

                val run =
                    runStub.getRun(
                        GetRunRequest.newBuilder()
                            .setTenantId("tenant-a")
                            .setRunId("run-1")
                            .build(),
                    )
                assertEquals("run-1", run.runId)

                val streamedEvents =
                    runStub.streamRunEvents(
                        StreamRunEventsRequest.newBuilder()
                            .setTenantId("tenant-a")
                            .setRunId("run-1")
                            .setSinceMs(0)
                            .setLimit(50)
                            .build(),
                    ).toList()
                assertTrue(streamedEvents.any { it.eventType == "run.started" })
                assertTrue(streamedEvents.any { it.eventType == "gateway.phase.entered" })
                val lastEvent = streamedEvents.last()
                val resumedEvents =
                    runStub.streamRunEvents(
                        StreamRunEventsRequest.newBuilder()
                            .setTenantId("tenant-a")
                            .setRunId("run-1")
                            .setSinceMs(lastEvent.createdAtMs)
                            .setSinceSeq(lastEvent.seq)
                            .setLimit(50)
                            .build(),
                    ).toList()
                assertTrue(resumedEvents.isEmpty())

                val score =
                    metricsStub.getReproducibilityScore(
                        GetReproducibilityScoreRequest.newBuilder()
                            .setTenantId("tenant-a")
                            .setRunId("run-1")
                            .build(),
                    )
                assertEquals("run-1", score.runId)
                assertTrue(score.score in 0.0..1.0)

                val report =
                    metricsStub.exportRunReport(
                        ExportRunReportRequest.newBuilder()
                            .setTenantId("tenant-a")
                            .setRunId("run-1")
                            .build(),
                    )
                assertEquals("run-1", report.runId)
                assertTrue(report.eventsJson.contains("run.started"))
                assertTrue(report.evidenceChainValid)
            }
        } finally {
            channel.shutdownNow()
            server.shutdownNow()
            if (dataSource is AutoCloseable) {
                dataSource.close()
            }
        }
    }

    private fun issueToken(
        role: String,
        tenantId: String,
    ): String {
        return JWT.create()
            .withIssuer(TEST_ISSUER)
            .withAudience(TEST_AUDIENCE)
            .withSubject("integration-user")
            .withClaim("roles", listOf(role))
            .withClaim("tenantId", tenantId)
            .sign(Algorithm.HMAC256(TEST_SECRET))
    }

    private fun attachToken(
        channel: io.grpc.ManagedChannel,
        token: String,
    ): io.grpc.Channel {
        val metadata =
            Metadata().apply {
                put(AUTHORIZATION_HEADER, "Bearer $token")
            }
        return ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(metadata))
    }

    companion object {
        private const val TEST_ISSUER = "neogenesis-auth"
        private const val TEST_AUDIENCE = "neogenesis-api"
        private const val TEST_SECRET = "regenops-integration-secret-with-at-least-32-chars"
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
