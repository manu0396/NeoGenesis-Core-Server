package com.neogenesis.server

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.ControlDecisionService
import com.neogenesis.server.application.InMemoryTelemetrySnapshotService
import com.neogenesis.server.application.clinical.ClinicalIntegrationService
import com.neogenesis.server.application.clinical.ClinicalValidationService
import com.neogenesis.server.application.clinical.ClinicalDimseService
import com.neogenesis.server.application.clinical.ClinicalPacsService
import com.neogenesis.server.application.clinical.FhirCohortAnalyticsService
import com.neogenesis.server.application.clinical.Hl7MllpGatewayService
import com.neogenesis.server.application.compliance.ComplianceTraceabilityService
import com.neogenesis.server.application.compliance.GdprService
import com.neogenesis.server.application.compliance.RegulatoryComplianceService
import com.neogenesis.server.application.error.BadRequestException
import com.neogenesis.server.application.error.ConflictException
import com.neogenesis.server.application.error.DependencyUnavailableException
import com.neogenesis.server.application.resilience.IntegrationResilienceExecutor
import com.neogenesis.server.application.resilience.RequestIdempotencyService
import com.neogenesis.server.application.serverless.OutboxRetryPolicy
import com.neogenesis.server.application.retina.RetinalPlanningService
import com.neogenesis.server.application.serverless.ServerlessDispatchService
import com.neogenesis.server.application.session.PrintSessionService
import com.neogenesis.server.application.sre.LatencyBudgetService
import com.neogenesis.server.application.telemetry.ClosedLoopControlService
import com.neogenesis.server.application.telemetry.AdvancedBioSimulationService
import com.neogenesis.server.application.telemetry.TelemetryProcessingService
import com.neogenesis.server.application.twin.DigitalTwinService
import com.neogenesis.server.domain.policy.DefaultTelemetrySafetyPolicy
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.config.ProductionReadinessValidator
import com.neogenesis.server.infrastructure.clinical.DicomWebClient
import com.neogenesis.server.infrastructure.clinical.DimseCommandClient
import com.neogenesis.server.infrastructure.clinical.Hl7MllpClient
import com.neogenesis.server.infrastructure.clinical.Hl7MllpServer
import com.neogenesis.server.infrastructure.grpc.BioPrintGrpcService
import com.neogenesis.server.infrastructure.grpc.GrpcCorrelationTracingInterceptor
import com.neogenesis.server.infrastructure.grpc.GrpcJwtAuthInterceptor
import com.neogenesis.server.infrastructure.grpc.GrpcServerFactory
import com.neogenesis.server.infrastructure.mqtt.TelemetryMqttAdapter
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import com.neogenesis.server.infrastructure.observability.OpenTelemetrySetup
import com.neogenesis.server.infrastructure.persistence.DatabaseFactory
import com.neogenesis.server.infrastructure.persistence.JdbcAuditEventStore
import com.neogenesis.server.infrastructure.persistence.JdbcClinicalDocumentStore
import com.neogenesis.server.infrastructure.persistence.JdbcControlCommandStore
import com.neogenesis.server.infrastructure.persistence.JdbcDigitalTwinStore
import com.neogenesis.server.infrastructure.persistence.JdbcLatencyBreachStore
import com.neogenesis.server.infrastructure.persistence.JdbcOutboxEventStore
import com.neogenesis.server.infrastructure.persistence.JdbcPrintSessionStore
import com.neogenesis.server.infrastructure.persistence.JdbcRequestIdempotencyStore
import com.neogenesis.server.infrastructure.persistence.JdbcRegulatoryStore
import com.neogenesis.server.infrastructure.persistence.JdbcRetinalPlanStore
import com.neogenesis.server.infrastructure.persistence.JdbcTelemetryEventStore
import com.neogenesis.server.infrastructure.persistence.JdbcGdprStore
import com.neogenesis.server.infrastructure.serverless.OutboxEventPublisherFactory
import com.neogenesis.server.infrastructure.security.configureAuthentication
import com.neogenesis.server.infrastructure.security.configureHttpProxyMutualTlsValidation
import com.neogenesis.server.infrastructure.security.SecurityPluginException
import com.neogenesis.server.infrastructure.security.SecretResolver
import com.neogenesis.server.infrastructure.security.JwtVerifierFactory
import com.neogenesis.server.infrastructure.security.DataProtectionService
import com.neogenesis.server.presentation.http.clinicalRoutes
import com.neogenesis.server.presentation.http.complianceRoutes
import com.neogenesis.server.presentation.http.digitalTwinRoutes
import com.neogenesis.server.presentation.http.gdprRoutes
import com.neogenesis.server.presentation.http.healthRoutes
import com.neogenesis.server.presentation.http.integrationRoutes
import com.neogenesis.server.presentation.http.observabilityRoutes
import com.neogenesis.server.presentation.http.printSessionRoutes
import com.neogenesis.server.presentation.http.regulatoryRoutes
import com.neogenesis.server.presentation.http.retinaRoutes
import com.neogenesis.server.presentation.http.sreRoutes
import com.neogenesis.server.presentation.http.telemetryRoutes
import io.grpc.ServerInterceptors
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import org.slf4j.MDC
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val appConfig = AppConfig.from(environment.config)
    val secretResolver = SecretResolver(appConfig.secrets)
    val resolvedJwtSecret = secretResolver.resolve(appConfig.security.jwt.secret)
    val resolvedPhiKey = secretResolver.resolve(appConfig.encryption.phiKeyRef)
    val resolvedPiiKey = secretResolver.resolve(appConfig.encryption.piiKeyRef)
    ProductionReadinessValidator.validate(appConfig, resolvedJwtSecret, resolvedPhiKey, resolvedPiiKey)
    val jwtVerifier = JwtVerifierFactory.create(appConfig.security.jwt, secretResolver)
    val dataSource = DatabaseFactory(appConfig.database).initialize()
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val metricsService = OperationalMetricsService(meterRegistry)
    val openTelemetry = OpenTelemetrySetup.initialize(appConfig.observability)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        retrieveFromHeader("X-Correlation-Id")
        generate { java.util.UUID.randomUUID().toString() }
        verify { it.isNotBlank() }
        replyToHeader(HttpHeaders.XRequestId)
        replyToHeader("X-Correlation-Id")
    }

    install(
        createApplicationPlugin("CorrelationIdResponseHeader") {
            onCallRespond { call, _ ->
                val correlationId = call.callId ?: java.util.UUID.randomUUID().toString()
                call.response.headers.append("X-Correlation-Id", correlationId, safeOnly = false)
            }
        }
    )

    install(CallLogging) {
        level = Level.INFO
    }

    install(
        createApplicationPlugin("RequestMdcContext") {
            onCall { call ->
                MDC.put("correlation_id", call.callId ?: "no-correlation-id")
                MDC.put("method", call.request.httpMethod.value)
                MDC.put("path", call.request.path())
            }
            onCallRespond { _, _ ->
                MDC.remove("correlation_id")
                MDC.remove("method")
                MDC.remove("path")
            }
            on(CallFailed) { _, _ ->
                MDC.remove("correlation_id")
                MDC.remove("method")
                MDC.remove("path")
            }
        }
    )

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    install(StatusPages) {
        exception<SecurityPluginException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.code))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.code))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse(cause.code))
        }
        exception<DependencyUnavailableException> { call, cause ->
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse(cause.code))
        }
        exception<Throwable> { call, cause ->
            this@module.environment.log.error("Unhandled server error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_server_error"))
        }
    }

    install(MicrometerMetrics) {
        registry = meterRegistry
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            UptimeMetrics()
        )
    }
    OpenTelemetrySetup.run { installHttpTracing(openTelemetry) }

    configureAuthentication(appConfig.security.jwt, jwtVerifier)
    configureHttpProxyMutualTlsValidation(appConfig.security.mtls.httpProxyValidation)

    val dataProtectionService = DataProtectionService(
        enabled = appConfig.encryption.enabled,
        phiKeyBase64 = resolvedPhiKey,
        piiKeyBase64 = resolvedPiiKey
    )
    val telemetrySnapshotService = InMemoryTelemetrySnapshotService()
    val telemetryEventStore = JdbcTelemetryEventStore(dataSource)
    val commandStore = JdbcControlCommandStore(dataSource)
    val auditEventStore = JdbcAuditEventStore(dataSource)
    val clinicalDocumentStore = JdbcClinicalDocumentStore(
        dataSource = dataSource,
        dataProtectionService = dataProtectionService,
        defaultRetentionDays = appConfig.gdpr.defaultRetentionDays
    )
    val digitalTwinStore = JdbcDigitalTwinStore(dataSource)
    val retinalPlanStore = JdbcRetinalPlanStore(dataSource)
    val printSessionStore = JdbcPrintSessionStore(dataSource)
    val latencyBreachStore = JdbcLatencyBreachStore(dataSource)
    val outboxEventStore = JdbcOutboxEventStore(dataSource)
    val requestIdempotencyStore = JdbcRequestIdempotencyStore(dataSource)
    val gdprStore = JdbcGdprStore(dataSource)
    val regulatoryStore = JdbcRegulatoryStore(dataSource)

    val auditTrailService = AuditTrailService(auditEventStore, metricsService)
    val gdprService = GdprService(
        gdprStore = gdprStore,
        auditTrailService = auditTrailService,
        gdprConfig = appConfig.gdpr
    )
    val regulatoryComplianceService = RegulatoryComplianceService(
        regulatoryStore = regulatoryStore,
        auditTrailService = auditTrailService,
        metricsService = metricsService
    )
    val traceabilityService = ComplianceTraceabilityService.fromClasspath()
    val integrationResilienceExecutor = IntegrationResilienceExecutor(
        enabled = appConfig.resilience.enabled,
        timeoutMs = appConfig.resilience.integrationTimeoutMs,
        failureThreshold = appConfig.resilience.circuitBreakerFailureThreshold,
        openStateMs = appConfig.resilience.circuitBreakerOpenStateMs,
        metricsService = metricsService
    )
    val requestIdempotencyService = RequestIdempotencyService(
        store = requestIdempotencyStore,
        metricsService = metricsService,
        ttlSeconds = appConfig.resilience.idempotencyTtlSeconds
    )
    val clinicalValidationService = ClinicalValidationService(appConfig.clinical.validation)
    val decisionService = ControlDecisionService(DefaultTelemetrySafetyPolicy())
    val digitalTwinService = DigitalTwinService(digitalTwinStore)
    val latencyBudgetService = LatencyBudgetService(
        thresholdMs = appConfig.control.latencyBudgetMs,
        latencyBreachStore = latencyBreachStore,
        auditTrailService = auditTrailService,
        metricsService = metricsService
    )
    val closedLoopControlService = ClosedLoopControlService(
        decisionService = decisionService,
        printSessionStore = printSessionStore,
        retinalPlanStore = retinalPlanStore
    )
    val advancedBioSimulationService = AdvancedBioSimulationService()

    val telemetryProcessingService = TelemetryProcessingService(
        closedLoopControlService = closedLoopControlService,
        advancedBioSimulationService = advancedBioSimulationService,
        telemetrySnapshotService = telemetrySnapshotService,
        telemetryEventStore = telemetryEventStore,
        controlCommandStore = commandStore,
        digitalTwinService = digitalTwinService,
        auditTrailService = auditTrailService,
        metricsService = metricsService,
        latencyBudgetService = latencyBudgetService
    )

    telemetryEventStore.recent(500).asReversed().forEach { historicalEvent ->
        telemetrySnapshotService.update(historicalEvent.telemetry)
    }

    val outboxPublisher = OutboxEventPublisherFactory.create(appConfig.serverless)
    val serverlessDispatchService = ServerlessDispatchService(
        outboxEventStore = outboxEventStore,
        metricsService = metricsService,
        outboxEventPublisher = outboxPublisher,
        retryPolicy = OutboxRetryPolicy(
            maxRetries = appConfig.serverless.maxRetries,
            baseBackoffMs = appConfig.serverless.baseBackoffMs,
            maxBackoffMs = appConfig.serverless.maxBackoffMs
        )
    )

    val clinicalIntegrationService = ClinicalIntegrationService(
        clinicalDocumentStore = clinicalDocumentStore,
        auditTrailService = auditTrailService,
        metricsService = metricsService,
        serverlessDispatchService = serverlessDispatchService,
        validationService = clinicalValidationService,
        gdprService = gdprService
    )
    val fhirCohortAnalyticsService = FhirCohortAnalyticsService(clinicalDocumentStore)
    val dicomWebClient = if (appConfig.clinical.dicomweb.enabled) DicomWebClient(appConfig.clinical.dicomweb) else null
    val dimseClient = if (appConfig.clinical.dimse.enabled) DimseCommandClient(appConfig.clinical.dimse) else null
    val clinicalPacsService = ClinicalPacsService(
        dicomWebClient = dicomWebClient,
        clinicalIntegrationService = clinicalIntegrationService,
        metricsService = metricsService,
        resilienceExecutor = integrationResilienceExecutor
    )
    val clinicalDimseService = ClinicalDimseService(
        dimseClient = dimseClient,
        resilienceExecutor = integrationResilienceExecutor
    )
    val hl7MllpGatewayService = Hl7MllpGatewayService(
        mllpClient = Hl7MllpClient(),
        clinicalIntegrationService = clinicalIntegrationService,
        mllpConfig = appConfig.clinical.hl7Mllp,
        metricsService = metricsService,
        resilienceExecutor = integrationResilienceExecutor
    )

    val retinalPlanningService = RetinalPlanningService(
        retinalPlanStore = retinalPlanStore,
        auditTrailService = auditTrailService,
        metricsService = metricsService
    )

    val printSessionService = PrintSessionService(
        printSessionStore = printSessionStore,
        auditTrailService = auditTrailService,
        metricsService = metricsService
    )

    val grpcServiceDefinition = ServerInterceptors.intercept(
        BioPrintGrpcService(telemetryProcessingService).bindService(),
        GrpcCorrelationTracingInterceptor(openTelemetry),
        GrpcJwtAuthInterceptor(jwtVerifier)
    )

    val grpcRuntime = GrpcServerFactory.build(
        grpcPort = appConfig.grpcPort,
        tlsConfig = appConfig.security.mtls.grpc,
        serviceDefinition = grpcServiceDefinition
    )
    val grpcServer = grpcRuntime.server

    runCatching {
        grpcServer.start()
        environment.log.info("gRPC server started on port ${appConfig.grpcPort}")
    }.onFailure { error ->
        runCatching { grpcRuntime.sslHotReloadManager?.close() }
        runCatching {
            if (outboxPublisher is AutoCloseable) {
                outboxPublisher.close()
            }
        }
        runCatching { closeDataSource(dataSource) }
        throw IllegalStateException("Failed to start gRPC server", error)
    }

    var mqttAdapter: TelemetryMqttAdapter? = null
    var hl7MllpServer: Hl7MllpServer? = null
    monitor.subscribe(ApplicationStarted) {
        appScope.launch {
            while (isActive) {
                runCatching {
                    serverlessDispatchService.drainPending(appConfig.serverless.outboxBatchSize)
                }.onFailure { error ->
                    environment.log.error("Outbox draining loop failed", error)
                }
                delay(appConfig.serverless.dispatchIntervalMs)
            }
        }

        appScope.launch {
            while (isActive) {
                runCatching {
                    gdprService.enforceRetention("system-retention")
                }.onFailure { error ->
                    environment.log.error("GDPR retention enforcement loop failed", error)
                }
                delay(6 * 60 * 60 * 1000L)
            }
        }

        appConfig.mqtt?.let { mqttConfig ->
            val adapter = TelemetryMqttAdapter(
                config = mqttConfig,
                mqttTlsConfig = appConfig.security.mtls.mqtt
            ) { telemetry ->
                telemetryProcessingService.process(
                    telemetry = telemetry,
                    source = "mqtt",
                    actor = "mqtt-broker"
                )
            }
            adapter.connect()
            adapter.subscribeToAllPrinters()
            mqttAdapter = adapter
            environment.log.info("MQTT adapter connected to ${mqttConfig.brokerUrl}")
        }

        if (appConfig.clinical.hl7Mllp.enabled) {
            val listener = Hl7MllpServer(
                host = appConfig.clinical.hl7Mllp.host,
                port = appConfig.clinical.hl7Mllp.port,
                maxFrameBytes = appConfig.clinical.hl7Mllp.maxFrameBytes,
                onMessage = hl7MllpGatewayService::onInboundMessage
            )
            listener.start()
            hl7MllpServer = listener
        }
    }

    monitor.subscribe(ApplicationStopping) {
        appScope.cancel()
        mqttAdapter?.close()
        hl7MllpServer?.close()
        integrationResilienceExecutor.close()
        grpcRuntime.sslHotReloadManager?.close()
        if (outboxPublisher is AutoCloseable) {
            outboxPublisher.close()
        }

        grpcServer.shutdown()
        if (!grpcServer.awaitTermination(5, TimeUnit.SECONDS)) {
            grpcServer.shutdownNow()
        }

        closeDataSource(dataSource)
        environment.log.info("NeoGenesis server stopped")
    }

    routing {
        healthRoutes(appConfig.grpcPort, traceabilityService, dataSource)
        telemetryRoutes(
            telemetrySnapshotService = telemetrySnapshotService,
            telemetryEventStore = telemetryEventStore,
            controlCommandStore = commandStore,
            telemetryProcessingService = telemetryProcessingService,
            metricsService = metricsService
        )
        digitalTwinRoutes(digitalTwinService, metricsService)
        clinicalRoutes(
            clinicalIntegrationService = clinicalIntegrationService,
            clinicalPacsService = clinicalPacsService,
            clinicalDimseService = clinicalDimseService,
            fhirCohortAnalyticsService = fhirCohortAnalyticsService,
            hl7MllpGatewayService = hl7MllpGatewayService,
            idempotencyService = requestIdempotencyService,
            requireIdempotencyKey = appConfig.resilience.requireIdempotencyKey,
            metricsService = metricsService
        )
        retinaRoutes(retinalPlanningService, metricsService)
        printSessionRoutes(printSessionService, metricsService)
        sreRoutes(latencyBudgetService, metricsService)
        integrationRoutes(serverlessDispatchService, metricsService, appConfig.serverless.outboxBatchSize)
        complianceRoutes(traceabilityService, auditTrailService, metricsService)
        regulatoryRoutes(regulatoryComplianceService, metricsService)
        gdprRoutes(gdprService, metricsService)
        observabilityRoutes(meterRegistry, appConfig.observability.metricsPath, metricsService)
    }
}

private fun closeDataSource(dataSource: DataSource) {
    if (dataSource is AutoCloseable) {
        dataSource.close()
    }
}

@Serializable
private data class ErrorResponse(val error: String)
