package com.neogenesis.server.infrastructure.grpc

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.neogenesis.platform.proto.v1.GetRunRequest
import com.neogenesis.platform.proto.v1.ListProtocolsRequest
import com.neogenesis.platform.proto.v1.ProtocolServiceGrpcKt
import com.neogenesis.platform.proto.v1.PublishVersionRequest
import com.neogenesis.platform.proto.v1.RunServiceGrpcKt
import com.neogenesis.platform.proto.v1.StartRunRequest
import com.neogenesis.server.application.regenops.RegenOpsService
import com.neogenesis.server.application.regenops.RegenTelemetryPoint
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.device.DevicePolicyRepository
import com.neogenesis.server.infrastructure.grpc.regenops.RegenProtocolV1GrpcService
import com.neogenesis.server.infrastructure.grpc.regenops.RegenRunV1GrpcService
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
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegenOpsV1GrpcIntegrationTest {
    @Test
    fun `v1 protocols and runs are bridged`() {
        val dataSource =
            DatabaseFactory(
                AppConfig.DatabaseConfig(
                    jdbcUrl = "jdbc:h2:mem:regenops-v1-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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

        val regenOpsStore = JdbcRegenOpsStore(dataSource)
        val regenOpsService = RegenOpsService(regenOpsStore)
        val verifier =
            JWT.require(Algorithm.HMAC256(TEST_SECRET))
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .build()

        val authInterceptor = GrpcJwtAuthInterceptor(verifier)
        val deviceInterceptor = GrpcDeviceContext.interceptor(DevicePolicyRepository())

        val serverName = InProcessServerBuilder.generateName()
        val server =
            InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(RegenProtocolV1GrpcService(regenOpsService), authInterceptor, deviceInterceptor))
                .addService(ServerInterceptors.intercept(RegenRunV1GrpcService(regenOpsService), authInterceptor, deviceInterceptor))
                .build()
                .start()

        val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()

        try {
            val token = issueToken(role = "regenops_operator", tenantId = "tenant-a")
            val protocolStub = ProtocolServiceGrpcKt.ProtocolServiceCoroutineStub(attachToken(channel, token))
            val runStub = RunServiceGrpcKt.RunServiceCoroutineStub(attachToken(channel, token))

            runBlocking {
                regenOpsService.createDraft(
                    tenantId = "tenant-a",
                    protocolId = "protocol-v1",
                    title = "Protocol V1",
                    contentJson = "{\"steps\":[\"seed\",\"grow\"]}",
                    actorId = "user-1",
                )

                val published =
                    protocolStub.publishVersion(
                        PublishVersionRequest.newBuilder()
                            .setProtocolId("protocol-v1")
                            .setVersionId("v1")
                            .build(),
                    )
                assertTrue(published.versionId.isNotBlank())
                assertNotNull(Instant.parse(published.createdAt))

                val listed = protocolStub.listProtocols(ListProtocolsRequest.newBuilder().build())
                assertTrue(listed.protocolsList.any { it.protocolId == "protocol-v1" })

                val started =
                    runStub.startRun(
                        StartRunRequest.newBuilder()
                            .setProtocolId("protocol-v1")
                            .setVersionId("v1")
                            .build(),
                    )
                assertTrue(started.runId.isNotBlank())

                regenOpsStore.appendRunEvent(
                    tenantId = "tenant-a",
                    runId = started.runId,
                    eventType = "gateway.event",
                    source = "gateway",
                    payloadJson = "{\"state\":\"active\"}",
                    createdAtMs = System.currentTimeMillis(),
                )

                regenOpsStore.appendTelemetry(
                    listOf(
                        RegenTelemetryPoint(
                            tenantId = "tenant-a",
                            runId = started.runId,
                            gatewayId = "gw-1",
                            metricKey = "pressure_kpa",
                            metricValue = 101.0,
                            unit = "kPa",
                            driftScore = 0.0,
                            recordedAtMs = System.currentTimeMillis(),
                        ),
                        RegenTelemetryPoint(
                            tenantId = "tenant-a",
                            runId = started.runId,
                            gatewayId = "gw-1",
                            metricKey = "temperature.c",
                            metricValue = 37.5,
                            unit = "C",
                            driftScore = 0.0,
                            recordedAtMs = System.currentTimeMillis(),
                        ),
                        RegenTelemetryPoint(
                            tenantId = "tenant-a",
                            runId = started.runId,
                            gatewayId = "gw-1",
                            metricKey = "pid_p",
                            metricValue = 0.12,
                            unit = "gain",
                            driftScore = 0.0,
                            recordedAtMs = System.currentTimeMillis(),
                        ),
                    ),
                )

                val events =
                    runStub.streamRunEvents(
                        GetRunRequest.newBuilder().setRunId(started.runId).build(),
                    ).toList()
                assertTrue(events.isNotEmpty())
                assertTrue(events.any { it.eventType == "run.started" })

                val telemetry =
                    runStub.streamTelemetry(
                        GetRunRequest.newBuilder().setRunId(started.runId).build(),
                    ).toList()
                assertTrue(telemetry.isNotEmpty())
                assertTrue(telemetry.any { it.pressureKpa == 101.0 })
                assertTrue(telemetry.any { it.temperatureC == 37.5 })
                assertTrue(telemetry.any { it.pidP == 0.12 })
            }
        } finally {
            GrpcCapabilityGuard.auditTrailService = null
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
                put(DEVICE_CLASS_HEADER, "WINDOWS_DESKTOP")
                put(DEVICE_TIER_HEADER, "TIER_1")
                put(DEVICE_ID_HEADER, "test-device-1")
                put(APP_VERSION_HEADER, "1.0.0-test")
                put(PLATFORM_HEADER, "desktop")
            }
        return ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(metadata))
    }

    companion object {
        private const val TEST_ISSUER = "neogenesis-auth"
        private const val TEST_AUDIENCE = "neogenesis-api"
        private const val TEST_SECRET = "regenops-integration-secret-with-at-least-32-chars"
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        private val DEVICE_ID_HEADER: Metadata.Key<String> =
            Metadata.Key.of("x-device-id", Metadata.ASCII_STRING_MARSHALLER)
        private val DEVICE_CLASS_HEADER: Metadata.Key<String> =
            Metadata.Key.of("x-device-class", Metadata.ASCII_STRING_MARSHALLER)
        private val DEVICE_TIER_HEADER: Metadata.Key<String> =
            Metadata.Key.of("x-device-tier", Metadata.ASCII_STRING_MARSHALLER)
        private val APP_VERSION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("x-app-version", Metadata.ASCII_STRING_MARSHALLER)
        private val PLATFORM_HEADER: Metadata.Key<String> =
            Metadata.Key.of("x-platform", Metadata.ASCII_STRING_MARSHALLER)
    }
}




