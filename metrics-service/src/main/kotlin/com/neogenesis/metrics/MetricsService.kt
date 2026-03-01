package com.neogenesis.metrics

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
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
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

fun main() {
    val config = MetricsConfig.fromEnv()
    val store = MetricsStore(config.storageDir)
    val server =
        embeddedServer(Netty, port = config.port) {
            metricsModule(config, store)
        }
    server.start(wait = true)
}

internal fun Application.metricsModule(config: MetricsConfig) {
    metricsModule(config, MetricsStore(config.storageDir))
}

private fun Application.metricsModule(
    config: MetricsConfig,
    store: MetricsStore,
) {
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
            val context = call.requireContext()
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
            val context = call.requireContext()
            recordAuditEvent("metrics.alerts.read", context)
            val filters =
                AlertsQuery(
                    tenantId = context.tenantId,
                    runId = call.request.queryParameters["runId"],
                    protocolId = call.request.queryParameters["protocolId"],
                    instrumentId = call.request.queryParameters["instrumentId"],
                    metricKey = call.request.queryParameters["metricKey"],
                    since = call.request.queryParameters["since"],
                )
            call.respond(MetricsAlertsResponse(alerts = store.listAlerts(filters)))
        }

        post("/metrics/ingest") {
            val context = call.requireContext()
            val payload = call.receive<MetricsIngestRequest>()
            val sample =
                MetricSample(
                    id = UUID.randomUUID().toString(),
                    tenantId = context.tenantId,
                    actorId = context.actorId,
                    correlationId = context.correlationId,
                    protocolId = payload.protocolId.orEmpty(),
                    instrumentId = payload.instrumentId.orEmpty(),
                    runId = payload.runId.orEmpty(),
                    metricKey = payload.metricKey.ifBlank { payload.metricType },
                    value = payload.value,
                    timestamp = payload.timestamp ?: Instant.now().toString(),
                )
            store.appendSample(sample)
            val alerts = store.evaluateSample(sample)
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

        get("/metrics/baselines") {
            val context = call.requireContext()
            recordAuditEvent("metrics.baselines.list", context)
            val response =
                store.listBaselines(
                    tenantId = context.tenantId,
                    protocolId = call.request.queryParameters["protocolId"],
                    instrumentId = call.request.queryParameters["instrumentId"],
                    metricKey = call.request.queryParameters["metricKey"],
                )
            call.respond(MetricsBaselinesResponse(baselines = response))
        }

        post("/metrics/baselines") {
            val context = call.requireContext()
            val request = call.receive<MetricsBaselineUpsertRequest>()
            val baseline =
                store.upsertBaseline(
                    BaselineDefinition(
                        tenantId = context.tenantId,
                        protocolId = request.protocolId,
                        instrumentId = request.instrumentId,
                        metricKey = request.metricKey,
                        mean = request.mean,
                        stddev = request.stddev,
                        windowSize = request.windowSize,
                        updatedBy = context.actorId,
                        updatedAt = Instant.now().toString(),
                        createdAt = request.createdAt ?: Instant.now().toString(),
                    ),
                )
            recordAuditEvent("metrics.baselines.upsert", context, mapOf("metricKey" to request.metricKey))
            call.respond(baseline)
        }

        get("/metrics/rules") {
            val context = call.requireContext()
            recordAuditEvent("metrics.rules.list", context)
            call.respond(MetricsRulesResponse(rules = store.listRules(context.tenantId)))
        }

        post("/metrics/rules") {
            val context = call.requireContext()
            val request = call.receive<MetricsRuleUpsertRequest>()
            val rule =
                store.upsertRule(
                    DriftRule(
                        id = request.id ?: UUID.randomUUID().toString(),
                        tenantId = context.tenantId,
                        protocolId = request.protocolId,
                        instrumentId = request.instrumentId,
                        metricKey = request.metricKey,
                        type = request.type,
                        ewmaAlpha = request.ewmaAlpha,
                        ewmaZThreshold = request.ewmaZThreshold,
                        cusumK = request.cusumK,
                        cusumH = request.cusumH,
                        severity = request.severity ?: "warn",
                        enabled = request.enabled,
                        updatedBy = context.actorId,
                        updatedAt = Instant.now().toString(),
                    ),
                )
            recordAuditEvent("metrics.rules.upsert", context, mapOf("metricKey" to request.metricKey))
            call.respond(rule)
        }

        get("/metrics/reports/run/{runId}") {
            val context = call.requireContext()
            val runId = call.parameters["runId"]?.trim().orEmpty()
            if (runId.isBlank()) {
                throw MetricsApiException("invalid_request", "runId is required")
            }
            recordAuditEvent("metrics.reports.run", context)
            call.respond(store.buildRunReport(context.tenantId, runId))
        }

        get("/metrics/reports/weekly") {
            val context = call.requireContext()
            val weekStart = call.request.queryParameters["weekStart"]?.trim().orEmpty()
            if (weekStart.isBlank()) {
                throw MetricsApiException("invalid_request", "weekStart is required (YYYY-MM-DD)")
            }
            recordAuditEvent("metrics.reports.weekly", context)
            call.respond(store.buildWeeklyReport(context.tenantId, weekStart))
        }
    }
}

data class MetricsConfig(
    val enabled: Boolean,
    val port: Int,
    val storageDir: String,
) {
    companion object {
        fun fromEnv(): MetricsConfig {
            val enabled =
                System.getenv("METRICS_SERVICE_MODE")
                    ?.equals("true", ignoreCase = true)
                    ?: false
            val port = System.getenv("METRICS_SERVICE_PORT")?.toIntOrNull() ?: 9090
            val storageDir = System.getenv("METRICS_SERVICE_STORE_DIR")?.trim().ifNullOrBlank { "metrics-service-data" }
            return MetricsConfig(
                enabled = enabled,
                port = port,
                storageDir = storageDir,
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
    val tenantId: String,
    val protocolId: String,
    val instrumentId: String,
    val runId: String,
    val metricKey: String,
    val value: Double,
    val createdAt: String,
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
    val protocolId: String? = null,
    val instrumentId: String? = null,
    val runId: String? = null,
    val metricKey: String = "",
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

@Serializable
data class MetricsBaselineUpsertRequest(
    val protocolId: String,
    val instrumentId: String,
    val metricKey: String,
    val mean: Double,
    val stddev: Double,
    val windowSize: Int,
    val createdAt: String? = null,
)

@Serializable
data class MetricsBaselinesResponse(
    val baselines: List<BaselineDefinition>,
)

@Serializable
data class MetricsRuleUpsertRequest(
    val id: String? = null,
    val protocolId: String,
    val instrumentId: String,
    val metricKey: String,
    val type: DriftRuleType,
    val ewmaAlpha: Double? = null,
    val ewmaZThreshold: Double? = null,
    val cusumK: Double? = null,
    val cusumH: Double? = null,
    val severity: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class MetricsRulesResponse(
    val rules: List<DriftRule>,
)

@Serializable
data class MetricsRunReport(
    val tenantId: String,
    val runId: String,
    val protocolId: String?,
    val instrumentId: String?,
    val metricKey: String?,
    val count: Int,
    val mean: Double,
    val stddev: Double,
    val lastValue: Double?,
    val lastTimestamp: String?,
    val alertCount: Int,
)

@Serializable
data class MetricsWeeklyReport(
    val tenantId: String,
    val weekStart: String,
    val weekEnd: String,
    val totalSamples: Int,
    val totalAlerts: Int,
)

@Serializable
data class BaselineDefinition(
    val tenantId: String,
    val protocolId: String,
    val instrumentId: String,
    val metricKey: String,
    val mean: Double,
    val stddev: Double,
    val windowSize: Int,
    val updatedBy: String,
    val updatedAt: String,
    val createdAt: String,
)

@Serializable
data class DriftRule(
    val id: String,
    val tenantId: String,
    val protocolId: String,
    val instrumentId: String,
    val metricKey: String,
    val type: DriftRuleType,
    val ewmaAlpha: Double? = null,
    val ewmaZThreshold: Double? = null,
    val cusumK: Double? = null,
    val cusumH: Double? = null,
    val severity: String,
    val enabled: Boolean,
    val updatedBy: String,
    val updatedAt: String,
)

@Serializable
enum class DriftRuleType {
    EWMA,
    CUSUM,
}

@Serializable
data class MetricSample(
    val id: String,
    val tenantId: String,
    val actorId: String,
    val correlationId: String,
    val protocolId: String,
    val instrumentId: String,
    val runId: String,
    val metricKey: String,
    val value: Double,
    val timestamp: String,
)

private data class AlertsQuery(
    val tenantId: String,
    val runId: String?,
    val protocolId: String?,
    val instrumentId: String?,
    val metricKey: String?,
    val since: String?,
)

private class MetricsStore(
    baseDirPath: String,
) {
    private val baseDir = Path.of(baseDirPath)
    private val baselinesPath = baseDir.resolve("baselines.json")
    private val rulesPath = baseDir.resolve("rules.json")
    private val metricsPath = baseDir.resolve("metrics.jsonl")
    private val alertsPath = baseDir.resolve("alerts.jsonl")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val baselines = loadList(baselinesPath, BaselineDefinition.serializer()).toMutableList()
    private val rules = loadList(rulesPath, DriftRule.serializer()).toMutableList()
    private val ewmaState = mutableMapOf<String, Double>()
    private val cusumState = mutableMapOf<String, Pair<Double, Double>>()

    init {
        Files.createDirectories(baseDir)
    }

    fun upsertBaseline(baseline: BaselineDefinition): BaselineDefinition {
        val index =
            baselines.indexOfFirst {
                it.tenantId == baseline.tenantId &&
                    it.protocolId == baseline.protocolId &&
                    it.instrumentId == baseline.instrumentId &&
                    it.metricKey == baseline.metricKey
            }
        if (index >= 0) {
            baselines[index] = baseline
        } else {
            baselines += baseline
        }
        saveList(baselinesPath, baselines, BaselineDefinition.serializer())
        return baseline
    }

    fun listBaselines(
        tenantId: String,
        protocolId: String?,
        instrumentId: String?,
        metricKey: String?,
    ): List<BaselineDefinition> {
        return baselines.filter {
            it.tenantId == tenantId &&
                (protocolId == null || it.protocolId == protocolId) &&
                (instrumentId == null || it.instrumentId == instrumentId) &&
                (metricKey == null || it.metricKey == metricKey)
        }
    }

    fun upsertRule(rule: DriftRule): DriftRule {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            rules[index] = rule
        } else {
            rules += rule
        }
        saveList(rulesPath, rules, DriftRule.serializer())
        return rule
    }

    fun listRules(tenantId: String): List<DriftRule> {
        return rules.filter { it.tenantId == tenantId }
    }

    fun appendSample(sample: MetricSample) {
        appendJsonLine(metricsPath, json.encodeToString(MetricSample.serializer(), sample))
    }

    fun listAlerts(filters: AlertsQuery): List<MetricsAlert> {
        return readJsonLines(alertsPath, MetricsAlert.serializer())
            .filter { it.tenantId == filters.tenantId }
            .filter { filters.runId == null || it.runId == filters.runId }
            .filter { filters.protocolId == null || it.protocolId == filters.protocolId }
            .filter { filters.instrumentId == null || it.instrumentId == filters.instrumentId }
            .filter { filters.metricKey == null || it.metricKey == filters.metricKey }
            .filter { filters.since == null || it.createdAt >= filters.since }
    }

    fun evaluateSample(sample: MetricSample): List<MetricsAlert> {
        val baseline =
            baselines.firstOrNull {
                it.tenantId == sample.tenantId &&
                    it.protocolId == sample.protocolId &&
                    it.instrumentId == sample.instrumentId &&
                    it.metricKey == sample.metricKey
            } ?: return emptyList()

        val alerts = mutableListOf<MetricsAlert>()
        val applicableRules =
            rules.filter {
                it.enabled &&
                    it.tenantId == sample.tenantId &&
                    it.protocolId == sample.protocolId &&
                    it.instrumentId == sample.instrumentId &&
                    it.metricKey == sample.metricKey
            }
        applicableRules.forEach { rule ->
            when (rule.type) {
                DriftRuleType.EWMA -> {
                    val alpha = rule.ewmaAlpha ?: 0.3
                    val key = stateKey(sample, rule.id)
                    val prev = ewmaState[key] ?: baseline.mean
                    val next = alpha * sample.value + (1 - alpha) * prev
                    ewmaState[key] = next
                    val std = baseline.stddev.takeIf { it > 0.0 } ?: return@forEach
                    val z = abs(next - baseline.mean) / std
                    val threshold = rule.ewmaZThreshold ?: 3.0
                    if (z >= threshold) {
                        alerts += buildAlert(sample, rule, "EWMA drift z=${"%.2f".format(z)}")
                    }
                }
                DriftRuleType.CUSUM -> {
                    val k = rule.cusumK ?: (baseline.stddev * 0.5)
                    val h = rule.cusumH ?: (baseline.stddev * 5.0)
                    val key = stateKey(sample, rule.id)
                    val prev = cusumState[key] ?: (0.0 to 0.0)
                    val sPos = max(0.0, prev.first + (sample.value - baseline.mean - k))
                    val sNeg = min(0.0, prev.second + (sample.value - baseline.mean + k))
                    cusumState[key] = sPos to sNeg
                    if (sPos > h || abs(sNeg) > h) {
                        alerts += buildAlert(sample, rule, "CUSUM drift s+=$sPos s-=$sNeg")
                    }
                }
            }
        }
        alerts.forEach { appendJsonLine(alertsPath, json.encodeToString(MetricsAlert.serializer(), it)) }
        return alerts
    }

    fun buildRunReport(tenantId: String, runId: String): MetricsRunReport {
        val samples = readJsonLines(metricsPath, MetricSample.serializer())
            .filter { it.tenantId == tenantId && it.runId == runId }
        if (samples.isEmpty()) {
            return MetricsRunReport(
                tenantId = tenantId,
                runId = runId,
                protocolId = null,
                instrumentId = null,
                metricKey = null,
                count = 0,
                mean = 0.0,
                stddev = 0.0,
                lastValue = null,
                lastTimestamp = null,
                alertCount = 0,
            )
        }
        val values = samples.map { it.value }
        val mean = values.average()
        val stddev = sqrt(values.map { (it - mean).pow(2) }.average())
        val last = samples.maxByOrNull { it.timestamp }
        val alertCount =
            readJsonLines(alertsPath, MetricsAlert.serializer())
                .count { it.tenantId == tenantId && it.runId == runId }
        return MetricsRunReport(
            tenantId = tenantId,
            runId = runId,
            protocolId = samples.first().protocolId,
            instrumentId = samples.first().instrumentId,
            metricKey = samples.first().metricKey,
            count = samples.size,
            mean = mean,
            stddev = stddev,
            lastValue = last?.value,
            lastTimestamp = last?.timestamp,
            alertCount = alertCount,
        )
    }

    fun buildWeeklyReport(tenantId: String, weekStart: String): MetricsWeeklyReport {
        val start = LocalDate.parse(weekStart, DateTimeFormatter.ISO_DATE).atStartOfDay().toInstant(ZoneOffset.UTC)
        val end = start.plusSeconds(7 * 24 * 3600)
        val samples =
            readJsonLines(metricsPath, MetricSample.serializer())
                .filter { it.tenantId == tenantId }
                .filter { Instant.parse(it.timestamp).isAfter(start.minusSeconds(1)) && Instant.parse(it.timestamp).isBefore(end) }
        val alerts =
            readJsonLines(alertsPath, MetricsAlert.serializer())
                .filter { it.tenantId == tenantId }
                .filter { Instant.parse(it.createdAt).isAfter(start.minusSeconds(1)) && Instant.parse(it.createdAt).isBefore(end) }
        return MetricsWeeklyReport(
            tenantId = tenantId,
            weekStart = weekStart,
            weekEnd = end.toString(),
            totalSamples = samples.size,
            totalAlerts = alerts.size,
        )
    }

    private fun buildAlert(sample: MetricSample, rule: DriftRule, message: String): MetricsAlert {
        return MetricsAlert(
            id = UUID.randomUUID().toString(),
            severity = rule.severity,
            message = message,
            tenantId = sample.tenantId,
            protocolId = sample.protocolId,
            instrumentId = sample.instrumentId,
            runId = sample.runId,
            metricKey = sample.metricKey,
            value = sample.value,
            createdAt = Instant.now().toString(),
        )
    }

    private fun stateKey(sample: MetricSample, ruleId: String): String {
        return listOf(sample.tenantId, sample.protocolId, sample.instrumentId, sample.metricKey, ruleId).joinToString(":")
    }

    private fun <T> loadList(path: Path, serializer: kotlinx.serialization.KSerializer<T>): List<T> {
        if (!Files.exists(path)) return emptyList()
        val text = Files.readString(path, StandardCharsets.UTF_8).trim()
        if (text.isBlank()) return emptyList()
        return json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(serializer), text)
    }

    private fun <T> saveList(
        path: Path,
        values: List<T>,
        serializer: kotlinx.serialization.KSerializer<T>,
    ) {
        Files.createDirectories(path.parent)
        Files.writeString(
            path,
            json.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer), values),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    private fun appendJsonLine(path: Path, line: String) {
        Files.createDirectories(path.parent)
        val writer: BufferedWriter =
            Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        writer.use { it.appendLine(line) }
    }

    private fun <T> readJsonLines(path: Path, serializer: kotlinx.serialization.KSerializer<T>): List<T> {
        if (!Files.exists(path)) return emptyList()
        return Files.readAllLines(path, StandardCharsets.UTF_8)
            .filter { it.isNotBlank() }
            .mapNotNull {
                runCatching { json.decodeFromString(serializer, it) }.getOrNull()
            }
    }
}

private fun String?.ifNullOrBlank(defaultValue: () -> String): String {
    return if (this == null || this.isBlank()) defaultValue() else this
}
