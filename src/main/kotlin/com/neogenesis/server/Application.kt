package com.neogenesis.server

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.ControlDecisionService
import com.neogenesis.server.application.InMemoryTelemetrySnapshotService
import com.neogenesis.server.application.billing.BillingService
import com.neogenesis.server.application.regenops.RegenOpsService
import com.neogenesis.server.application.sre.LatencyBudgetService
import com.neogenesis.server.application.telemetry.AdvancedBioSimulationService
import com.neogenesis.server.application.telemetry.ClosedLoopControlService
import com.neogenesis.server.application.telemetry.TelemetryProcessingService
import com.neogenesis.server.application.twin.DigitalTwinService
import com.neogenesis.server.domain.policy.DefaultTelemetrySafetyPolicy
import com.neogenesis.server.infrastructure.billing.FakeBillingProvider
import com.neogenesis.server.infrastructure.billing.StripeBillingProvider
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.config.ProductionReadinessValidator
import com.neogenesis.server.infrastructure.grpc.BioPrintGrpcService
import com.neogenesis.server.infrastructure.grpc.GrpcCorrelationTracingInterceptor
import com.neogenesis.server.infrastructure.grpc.GrpcJwtAuthInterceptor
import com.neogenesis.server.infrastructure.grpc.GrpcServerFactory
import com.neogenesis.server.infrastructure.grpc.regenops.RegenGatewayGrpcService
import com.neogenesis.server.infrastructure.grpc.regenops.RegenMetricsGrpcService
import com.neogenesis.server.infrastructure.grpc.regenops.RegenProtocolGrpcService
import com.neogenesis.server.infrastructure.grpc.regenops.RegenRunGrpcService
import com.neogenesis.server.infrastructure.observability.OpenTelemetrySetup
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.BillingEventRepository
import com.neogenesis.server.infrastructure.persistence.BillingPlanRepository
import com.neogenesis.server.infrastructure.persistence.BillingSubscriptionRepository
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.DatabaseFactory
import com.neogenesis.server.infrastructure.persistence.DeviceRepository
import com.neogenesis.server.infrastructure.persistence.JdbcAuditEventStore
import com.neogenesis.server.infrastructure.persistence.JdbcControlCommandStore
import com.neogenesis.server.infrastructure.persistence.JdbcDigitalTwinStore
import com.neogenesis.server.infrastructure.persistence.JdbcLatencyBreachStore
import com.neogenesis.server.infrastructure.persistence.JdbcPrintSessionStore
import com.neogenesis.server.infrastructure.persistence.JdbcRegenOpsStore
import com.neogenesis.server.infrastructure.persistence.JdbcRetinalPlanStore
import com.neogenesis.server.infrastructure.persistence.JdbcTelemetryEventStore
import com.neogenesis.server.infrastructure.persistence.JobRepository
import com.neogenesis.server.infrastructure.persistence.TelemetryRepository
import com.neogenesis.server.infrastructure.persistence.TwinMetricsRepository
import com.neogenesis.server.infrastructure.persistence.UserRepository
import com.neogenesis.server.infrastructure.security.JwtVerifierFactory
import com.neogenesis.server.infrastructure.security.NeoGenesisPrincipal
import com.neogenesis.server.infrastructure.security.SecretResolver
import com.neogenesis.server.infrastructure.security.SecurityPluginException
import com.neogenesis.server.infrastructure.security.configureAuthentication
import com.neogenesis.server.infrastructure.security.configureHttpProxyMutualTlsValidation
import com.neogenesis.server.modules.ApiException
import com.neogenesis.server.modules.AuthTokenIssuer
import com.neogenesis.server.modules.BruteForceLimiter
import com.neogenesis.server.modules.ErrorResponse
import com.neogenesis.server.modules.InMemoryMetrics
import com.neogenesis.server.modules.PasswordService
import com.neogenesis.server.modules.auditModule
import com.neogenesis.server.modules.authModule
import com.neogenesis.server.modules.admin.adminWebModule
import com.neogenesis.server.modules.billingModule
import com.neogenesis.server.modules.bioinkModule
import com.neogenesis.server.modules.commercial.CommercialRepository
import com.neogenesis.server.modules.commercial.CommercialService
import com.neogenesis.server.modules.commercial.commercialModule
import com.neogenesis.server.modules.connectors.connectorCertificationModule
import com.neogenesis.server.modules.devicesModule
import com.neogenesis.server.modules.healthModule
import com.neogenesis.server.modules.jobsModule
import com.neogenesis.server.modules.shouldBootstrapAdmin
import com.neogenesis.server.modules.telemetryModule
import io.grpc.ServerInterceptors
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.slf4j.MDC
import org.slf4j.event.Level
import java.io.File
import java.net.URI
import java.util.UUID

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val appConfig = AppConfig.from(environment.config)
    val secretResolver = SecretResolver(appConfig.secrets)
    val resolvedJwtSecret = secretResolver.resolve(appConfig.security.jwt.secret) ?: appConfig.jwt.secret
    val resolvedSecurityConfig =
        appConfig.security.copy(
            jwt = appConfig.security.jwt.copy(secret = resolvedJwtSecret),
        )
    val resolvedPhiKey = secretResolver.resolve(appConfig.encryption.phiKeyRef)
    val resolvedPiiKey = secretResolver.resolve(appConfig.encryption.piiKeyRef)
    ProductionReadinessValidator.validate(appConfig, resolvedJwtSecret, resolvedPhiKey, resolvedPiiKey)

    val openTelemetry = OpenTelemetrySetup.initialize(appConfig.observability)
    val jwtVerifier = JwtVerifierFactory.create(resolvedSecurityConfig.jwt, secretResolver)
    val dataSource = DatabaseFactory(appConfig.database).initialize()
    val serverVersion = readServerVersion()

    val userRepository = UserRepository(dataSource)
    val deviceRepository = DeviceRepository(dataSource)
    val jobRepository = JobRepository(dataSource)
    val telemetryRepository = TelemetryRepository(dataSource)
    val twinMetricsRepository = TwinMetricsRepository(dataSource)
    val auditLogRepository = AuditLogRepository(dataSource)
    val passwordService = PasswordService()
    val metrics = InMemoryMetrics()
    val operationalMetrics = OperationalMetricsService(metrics.registry())
    val tokenIssuer = AuthTokenIssuer(appConfig.copy(security = resolvedSecurityConfig))
    val bruteForceLimiter = BruteForceLimiter(appConfig.rateLimits.authAttemptsPerMinute)
    val billingProvider =
        if (appConfig.billing.enabled && appConfig.billing.provider.equals("stripe", ignoreCase = true)) {
            StripeBillingProvider(appConfig.billing)
        } else {
            FakeBillingProvider()
        }
    val billingService =
        BillingService(
            config = appConfig.billing,
            planRepository = BillingPlanRepository(dataSource),
            subscriptionRepository = BillingSubscriptionRepository(dataSource),
            eventRepository = BillingEventRepository(dataSource),
            provider = billingProvider,
        )
    billingService.seedPlans()
    val auditEventStore = JdbcAuditEventStore(dataSource)
    val auditTrailService = AuditTrailService(auditEventStore, operationalMetrics)
    val commercialRepository = CommercialRepository(dataSource)
    val commercialService = CommercialService(commercialRepository, auditTrailService)

    if (shouldBootstrapAdmin(appConfig)) {
        val bootstrapUser = appConfig.adminBootstrap.user
        val bootstrapHash =
            when {
                !appConfig.adminBootstrap.passwordHash.isNullOrBlank() -> appConfig.adminBootstrap.passwordHash
                !appConfig.adminBootstrap.password.isNullOrBlank() -> passwordService.hash(appConfig.adminBootstrap.password)
                else -> null
            }
        if (!bootstrapUser.isNullOrBlank() && !bootstrapHash.isNullOrBlank()) {
            userRepository.upsertBootstrapAdmin(
                username = bootstrapUser.trim(),
                passwordHash = bootstrapHash,
                role = CanonicalRole.ADMIN,
            )
        } else if (appConfig.adminBootstrap.enabled) {
            error("ADMIN_BOOTSTRAP_ENABLED=true requires ADMIN_BOOTSTRAP_USER and password/hash")
        }
    }

    install(CallId) {
        retrieve { call ->
            call.request.headers["X-Correlation-Id"]
                ?: call.request.headers[HttpHeaders.XRequestId]
        }
        generate { UUID.randomUUID().toString() }
        verify { it.isNotBlank() }
        replyToHeader("X-Correlation-Id")
        replyToHeader(HttpHeaders.XRequestId)
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(
        createApplicationPlugin("RequestContextPlugin") {
            val startedAt = io.ktor.util.AttributeKey<Long>("request-started-at")
            onCall { call ->
                call.attributes.put(startedAt, System.currentTimeMillis())
                MDC.put("env", appConfig.env)
                MDC.put("service", appConfig.observability.serviceName)
                MDC.put("version", serverVersion)
                MDC.put("traceId", call.callId ?: "missing-trace-id")
                MDC.put("correlationId", call.callId ?: "missing-correlation-id")
                MDC.put("endpoint", "${call.request.httpMethod.value} ${call.request.path()}")
            }
            onCallRespond { call, _ ->
                val elapsed = System.currentTimeMillis() - call.attributes[startedAt]
                val principal = call.principal<NeoGenesisPrincipal>()
                if (principal != null) {
                    MDC.put("userId", principal.subject)
                    principal.tenantId?.let { MDC.put("tenantId", it) }
                }
                MDC.put("status", call.response.status()?.value?.toString() ?: "200")
                MDC.put("durationMs", elapsed.toString())
                this@module.environment.log.info("request.complete")
                clearMdc()
            }
            on(CallFailed) { _, _ ->
                clearMdc()
            }
        },
    )

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                ignoreUnknownKeys = true
                isLenient = true
            },
        )
    }

    install(MicrometerMetrics) {
        registry = metrics.registry()
    }

    OpenTelemetrySetup.run {
        installHttpTracing(openTelemetry)
    }
    configureHttpProxyMutualTlsValidation(
        appConfig.security.mtls.httpProxyValidation,
        appConfig.observability.metricsPath,
    )

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        header("X-XSS-Protection", "0")
    }

    if (appConfig.corsAllowedOrigins.isNotEmpty()) {
        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.XRequestId)
            allowCredentials = true
            appConfig.corsAllowedOrigins.forEach { origin ->
                runCatching {
                    val uri = URI(origin)
                    if (!uri.host.isNullOrBlank() && !uri.scheme.isNullOrBlank()) {
                        allowHost(uri.host, schemes = listOf(uri.scheme))
                    }
                }
            }
        }
    }

    intercept(ApplicationCallPipeline.Call) {
        try {
            withTimeout(appConfig.rateLimits.requestTimeoutMs) {
                proceed()
            }
        } catch (_: TimeoutCancellationException) {
            throw ApiException(
                code = "request_timeout",
                message = "Request processing exceeded timeout",
                status = HttpStatusCode.RequestTimeout,
            )
        }
    }

    install(
        createApplicationPlugin("RequestSizeLimit") {
            onCall { call ->
                val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (contentLength != null && contentLength > appConfig.rateLimits.maxRequestBodyBytes) {
                    throw ApiException(
                        code = "payload_too_large",
                        message = "Request body exceeds size limit",
                        status = HttpStatusCode.PayloadTooLarge,
                    )
                }
            }
        },
    )

    install(StatusPages) {
        exception<SecurityPluginException> { call, cause ->
            call.respond(
                cause.status,
                ErrorResponse(
                    code = cause.code,
                    message = cause.code.replace('_', ' '),
                    traceId = call.callId ?: "missing-trace-id",
                ),
            )
        }
        exception<ApiException> { call, cause ->
            call.respond(
                cause.status,
                ErrorResponse(
                    code = cause.code,
                    message = cause.message,
                    traceId = call.callId ?: "missing-trace-id",
                ),
            )
        }
        exception<Throwable> { call, cause ->
            this@module.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    code = "internal_server_error",
                    message = "Unexpected server error",
                    traceId = call.callId ?: "missing-trace-id",
                ),
            )
        }
    }

    configureAuthentication(resolvedSecurityConfig.jwt, jwtVerifier)

    val grpcRuntime =
        if (!appConfig.env.equals("test", ignoreCase = true)) {
            try {
                val telemetrySnapshotService = InMemoryTelemetrySnapshotService()
                val telemetryEventStore = JdbcTelemetryEventStore(dataSource)
                val controlCommandStore = JdbcControlCommandStore(dataSource)
                val digitalTwinStore = JdbcDigitalTwinStore(dataSource)
                val latencyBreachStore = JdbcLatencyBreachStore(dataSource)
                val latencyBudgetService =
                    LatencyBudgetService(
                        thresholdMs = appConfig.control.latencyBudgetMs,
                        latencyBreachStore = latencyBreachStore,
                        auditTrailService = auditTrailService,
                        metricsService = operationalMetrics,
                    )

                val retinalPlanStore = JdbcRetinalPlanStore(dataSource)
                val printSessionStore = JdbcPrintSessionStore(dataSource)

                val closedLoopControlService =
                    ClosedLoopControlService(
                        decisionService = ControlDecisionService(DefaultTelemetrySafetyPolicy()),
                        printSessionStore = printSessionStore,
                        retinalPlanStore = retinalPlanStore,
                    )
                val telemetryProcessingService =
                    TelemetryProcessingService(
                        closedLoopControlService = closedLoopControlService,
                        advancedBioSimulationService = AdvancedBioSimulationService(),
                        telemetrySnapshotService = telemetrySnapshotService,
                        telemetryEventStore = telemetryEventStore,
                        controlCommandStore = controlCommandStore,
                        digitalTwinService = DigitalTwinService(digitalTwinStore),
                        auditTrailService = auditTrailService,
                        metricsService = operationalMetrics,
                        latencyBudgetService = latencyBudgetService,
                    )

                val jwtAuthInterceptor = GrpcJwtAuthInterceptor(jwtVerifier)
                val tracingInterceptor = GrpcCorrelationTracingInterceptor(openTelemetry)

                val grpcService = BioPrintGrpcService(telemetryProcessingService)
                val bioPrintServiceDefinition =
                    ServerInterceptors.intercept(
                        grpcService,
                        jwtAuthInterceptor,
                        tracingInterceptor,
                    )

                val regenOpsService = RegenOpsService(JdbcRegenOpsStore(dataSource))
                val protocolServiceDefinition =
                    ServerInterceptors.intercept(
                        RegenProtocolGrpcService(regenOpsService),
                        jwtAuthInterceptor,
                        tracingInterceptor,
                    )
                val runServiceDefinition =
                    ServerInterceptors.intercept(
                        RegenRunGrpcService(regenOpsService),
                        jwtAuthInterceptor,
                        tracingInterceptor,
                    )
                val gatewayServiceDefinition =
                    ServerInterceptors.intercept(
                        RegenGatewayGrpcService(regenOpsService),
                        jwtAuthInterceptor,
                        tracingInterceptor,
                    )
                val metricsServiceDefinition =
                    ServerInterceptors.intercept(
                        RegenMetricsGrpcService(regenOpsService),
                        jwtAuthInterceptor,
                        tracingInterceptor,
                    )

                val runtime =
                    GrpcServerFactory.build(
                        grpcPort = appConfig.grpcPort,
                        tlsConfig = appConfig.security.mtls.grpc,
                        serviceDefinitions =
                            listOf(
                                bioPrintServiceDefinition,
                                protocolServiceDefinition,
                                runServiceDefinition,
                                gatewayServiceDefinition,
                                metricsServiceDefinition,
                            ),
                    )
                runtime.server.start()
                environment.log.info("gRPC server started on port ${appConfig.grpcPort}")
                runtime
            } catch (error: Exception) {
                environment.log.error("Failed to start gRPC server", error)
                if (appConfig.env.equals("prod", ignoreCase = true) ||
                    appConfig.env.equals("production", ignoreCase = true)
                ) {
                    throw error
                }
                null
            }
        } else {
            environment.log.info("Skipping gRPC server startup for test environment")
            null
        }

    monitor.subscribe(ApplicationStopping) {
        runCatching { grpcRuntime?.server?.shutdown() }
        runCatching { grpcRuntime?.sslHotReloadManager?.close() }
        runCatching { (dataSource as? com.zaxxer.hikari.HikariDataSource)?.close() }
    }

    routing {
        healthModule(
            dataSource = dataSource,
            metrics = metrics,
            version = serverVersion,
            metricsPath = appConfig.observability.metricsPath,
        )
        authModule(
            userRepository = userRepository,
            passwordService = passwordService,
            tokenIssuer = tokenIssuer,
            bruteForceLimiter = bruteForceLimiter,
            metrics = metrics,
        )
        devicesModule(
            deviceRepository = deviceRepository,
            auditLogRepository = auditLogRepository,
            metrics = metrics,
        )
        jobsModule(
            deviceRepository = deviceRepository,
            jobRepository = jobRepository,
            auditLogRepository = auditLogRepository,
            metrics = metrics,
        )
        telemetryModule(
            jobRepository = jobRepository,
            telemetryRepository = telemetryRepository,
            twinMetricsRepository = twinMetricsRepository,
            auditLogRepository = auditLogRepository,
            metrics = metrics,
            maxPayloadBytes = appConfig.rateLimits.maxRequestBodyBytes,
        )
        auditModule(
            jobRepository = jobRepository,
            telemetryRepository = telemetryRepository,
            twinMetricsRepository = twinMetricsRepository,
            auditLogRepository = auditLogRepository,
            serverVersion = serverVersion,
            billingService = billingService,
        )
        billingModule(
            billingService = billingService,
        )
        if (appConfig.commercial.enabled) {
            commercialModule(
                service = commercialService,
            )
        }
        if (appConfig.connectorCertification.enabled) {
            connectorCertificationModule(
                auditTrailService = auditTrailService,
            )
        }
        if (appConfig.adminWeb.enabled) {
            adminWebModule(
                auditTrailService = auditTrailService,
            )
        }
        bioinkModule(
            jobRepository = jobRepository,
            auditLogRepository = auditLogRepository,
            metrics = metrics,
        )
    }
}

private fun clearMdc() {
    listOf("env", "service", "version", "traceId", "userId", "endpoint", "status", "durationMs").forEach {
        MDC.remove(it)
    }
}

private fun readServerVersion(): String {
    val fromEnv = System.getenv("APP_VERSION")?.trim()
    if (!fromEnv.isNullOrBlank()) {
        return fromEnv
    }
    return runCatching {
        File("backend/VERSION").readText(Charsets.UTF_8).trim()
    }.getOrElse { "1.0.0" }
}
