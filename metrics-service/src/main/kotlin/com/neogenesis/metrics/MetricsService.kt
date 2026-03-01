package com.neogenesis.metrics

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

fun main() {
    val config = MetricsConfig.fromEnv()
    val server =
        embeddedServer(Netty, port = config.port) {
            metricsModule(config)
        }
    server.start(wait = true)
}

fun Application.metricsModule(config: MetricsConfig) {
    installErrorHandling()
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                ignoreUnknownKeys = true
                isLenient = true
            },
        )
    }

    routing {
        get("/health") {
            call.respondText("ok")
        }

        if (!config.enabled) {
            return@routing
        }

        get("/metrics/score") {
            val context = requireContext()
            recordAuditEvent("metrics.score.read", context)
            call.respond(
                MetricsScoreResponse(
                    status = "ok",
                    tenantId = context.tenantId,
                    correlationId = context.correlationId,
                    generatedAt = Instant.now().toString(),
                ),
            )
        }

        get("/metrics/alerts") {
            val context = requireContext()
            recordAuditEvent("metrics.alerts.read", context)
            call.respond(MetricsAlertsResponse(alerts = emptyList()))
        }

        post("/metrics/ingest") {
            val context = requireContext()
            val payload = call.receive<MetricsIngestRequest>()
            recordAuditEvent(
                "metrics.ingest",
                context,
                mapOf(
                    "metricType" to payload.metricType,
                    "value" to payload.value.toString(),
                ),
            )
            call.respond(HttpStatusCode.Accepted, MetricsIngestResponse(status = "accepted"))
        }
    }
}

data class MetricsConfig(
    val enabled: Boolean,
    val port: Int,
) {
    companion object {
        fun fromEnv(): MetricsConfig {
            val enabled =
                System.getenv("METRICS_SERVICE_MODE")
                    ?.equals("true", ignoreCase = true)
                    ?: false
            val port = System.getenv("METRICS_SERVICE_PORT")?.toIntOrNull() ?: 9090
            return MetricsConfig(
                enabled = enabled,
                port = port,
            )
        }
    }
}

data class RequestContext(
    val tenantId: String,
    val correlationId: String,
    val actorId: String,
)

private fun io.ktor.server.application.ApplicationCall.requireContext(): RequestContext {
    val tenantId = request.headers["X-Tenant-Id"].orEmpty()
    val actorId = request.headers["X-Actor-Id"].orEmpty()
    val correlationId =
        request.headers["X-Correlation-Id"]
            ?: request.headers["X-Request-Id"]
            ?: ""
    if (tenantId.isBlank() || actorId.isBlank() || correlationId.isBlank()) {
        throw MetricsApiException(
            "missing_headers",
            "tenant_id, actor_id, and correlation_id headers are required",
        )
    }
    return RequestContext(
        tenantId = tenantId,
        correlationId = correlationId,
        actorId = actorId,
    )
}

private fun Application.installErrorHandling() {
    intercept(ApplicationCallPipeline.Call) {
        try {
            proceed()
        } catch (error: MetricsApiException) {
            call.respond(
                HttpStatusCode.BadRequest,
                MetricsErrorResponse(code = error.code, message = error.message),
            )
        }
    }
}

private fun recordAuditEvent(
    action: String,
    context: RequestContext,
    details: Map<String, String> = emptyMap(),
) {
    val payload =
        AuditEvent(
            tenantId = context.tenantId,
            actorId = context.actorId,
            correlationId = context.correlationId,
            action = action,
            details = details,
        )
    println(payload.toJson())
}

@Serializable
data class AuditEvent(
    val tenantId: String,
    val actorId: String,
    val correlationId: String,
    val action: String,
    val details: Map<String, String>,
    val createdAt: String = Instant.now().toString(),
) {
    fun toJson(): String {
        val detailsJson = details.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
        return """
            {
              "tenantId":"$tenantId",
              "actorId":"$actorId",
              "correlationId":"$correlationId",
              "action":"$action",
              "details":{$detailsJson},
              "createdAt":"$createdAt"
            }
        """.trimIndent()
    }
}

@Serializable
data class MetricsScoreResponse(
    val status: String,
    val tenantId: String,
    val correlationId: String,
    val generatedAt: String,
)

@Serializable
data class MetricsAlert(
    val id: String,
    val severity: String,
    val message: String,
)

@Serializable
data class MetricsAlertsResponse(
    val alerts: List<MetricsAlert>,
)

@Serializable
data class MetricsIngestRequest(
    val metricType: String,
    val value: Double,
    val timestamp: String? = null,
)

@Serializable
data class MetricsIngestResponse(
    val status: String,
)

class MetricsApiException(
    val code: String,
    override val message: String,
) : RuntimeException(message)

@Serializable
data class MetricsErrorResponse(
    val code: String,
    val message: String,
)
