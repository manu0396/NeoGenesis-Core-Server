package com.neogenesis.server.infrastructure.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val runtime: RuntimeConfig,
    val grpcPort: Int,
    val database: DatabaseConfig,
    val mqtt: MqttConfig?,
    val clinical: ClinicalConfig,
    val security: SecurityConfig,
    val secrets: SecretsConfig,
    val encryption: EncryptionConfig,
    val gdpr: GdprConfig,
    val observability: ObservabilityConfig,
    val resilience: ResilienceConfig,
    val control: ControlConfig,
    val serverless: ServerlessConfig
) {
    data class RuntimeConfig(
        val environment: String
    )

    data class DatabaseConfig(
        val jdbcUrl: String,
        val username: String,
        val password: String,
        val maximumPoolSize: Int,
        val migrateOnStartup: Boolean
    )

    data class MqttConfig(
        val brokerUrl: String,
        val clientId: String,
        val topicRoot: String,
        val username: String?,
        val password: String?
    )

    data class ClinicalConfig(
        val dicomweb: DicomWebConfig,
        val dimse: DimseConfig,
        val hl7Mllp: Hl7MllpConfig,
        val validation: ValidationConfig
    ) {
        data class DicomWebConfig(
            val enabled: Boolean,
            val baseUrl: String?,
            val bearerToken: String?,
            val timeoutMs: Long
        )

        data class DimseConfig(
            val enabled: Boolean,
            val findScuPath: String?,
            val moveScuPath: String?,
            val getScuPath: String?,
            val callingAeTitle: String,
            val calledAeTitle: String,
            val remoteHost: String,
            val remotePort: Int,
            val localStorePath: String?
        )

        data class Hl7MllpConfig(
            val enabled: Boolean,
            val host: String,
            val port: Int,
            val maxFrameBytes: Int,
            val connectTimeoutMs: Int,
            val readTimeoutMs: Int
        )

        data class ValidationConfig(
            val enforceStrict: Boolean,
            val allowedFhirVersions: Set<String>,
            val requiredFhirProfiles: Set<String>,
            val requiredTerminologySystems: Set<String>,
            val allowedHl7Versions: Set<String>,
            val allowedHl7MessageTypes: Set<String>
        )
    }

    data class SecurityConfig(
        val jwt: JwtConfig,
        val mtls: MtlsConfig
    ) {
        data class JwtConfig(
            val realm: String,
            val issuer: String,
            val audience: String,
            val secret: String,
            val mode: String,
            val jwksUrl: String?
        )

        data class MtlsConfig(
            val grpc: GrpcMtlsConfig,
            val mqtt: MqttMtlsConfig,
            val httpProxyValidation: HttpProxyValidationConfig
        ) {
            data class GrpcMtlsConfig(
                val enabled: Boolean,
                val requireClientAuth: Boolean,
                val certChainPath: String?,
                val privateKeyPath: String?,
                val trustCertPath: String?,
                val hotReloadEnabled: Boolean,
                val hotReloadIntervalMinutes: Long
            )

            data class MqttMtlsConfig(
                val enabled: Boolean,
                val keyStorePath: String?,
                val keyStorePassword: String?,
                val trustStorePath: String?,
                val trustStorePassword: String?
            )

            data class HttpProxyValidationConfig(
                val enabled: Boolean,
                val verifyHeaderName: String,
                val verifySuccessValue: String
            )
        }
    }

    data class SecretsConfig(
        val vaultEnabled: Boolean,
        val vaultAddress: String?,
        val vaultToken: String?,
        val vaultMount: String,
        val vaultTimeoutMs: Long,
        val kmsEnabled: Boolean,
        val kmsRegion: String?,
        val kmsEndpointOverride: String?
    )

    data class EncryptionConfig(
        val enabled: Boolean,
        val phiKeyRef: String,
        val piiKeyRef: String
    )

    data class GdprConfig(
        val enforceConsent: Boolean,
        val defaultRetentionDays: Int
    )

    data class ObservabilityConfig(
        val metricsPath: String,
        val otlpEndpoint: String?,
        val serviceName: String
    )

    data class ResilienceConfig(
        val enabled: Boolean,
        val integrationTimeoutMs: Long,
        val circuitBreakerFailureThreshold: Int,
        val circuitBreakerOpenStateMs: Long,
        val requireIdempotencyKey: Boolean,
        val idempotencyTtlSeconds: Long
    )

    data class ControlConfig(
        val latencyBudgetMs: Long
    )

    data class ServerlessConfig(
        val outboxBatchSize: Int,
        val dispatchIntervalMs: Long,
        val provider: String,
        val maxRetries: Int,
        val baseBackoffMs: Long,
        val maxBackoffMs: Long,
        val awsSqs: AwsSqsConfig,
        val awsEventBridge: AwsEventBridgeConfig,
        val gcpPubSub: GcpPubSubConfig
    ) {
        data class AwsSqsConfig(
            val queueUrl: String?,
            val region: String,
            val endpointOverride: String?
        )

        data class AwsEventBridgeConfig(
            val eventBusName: String?,
            val sourceNamespace: String,
            val region: String,
            val endpointOverride: String?
        )

        data class GcpPubSubConfig(
            val projectId: String?,
            val topicId: String?,
            val emulatorHost: String?
        )
    }

    companion object {
        fun from(config: ApplicationConfig): AppConfig {
            val runtimeConfig = RuntimeConfig(
                environment = config.string("neogenesis.runtime.environment") ?: "development"
            )
            val grpcPort = config.string("neogenesis.grpcPort")?.toIntOrNull() ?: 50051

            val databaseConfig = DatabaseConfig(
                jdbcUrl = config.string("neogenesis.database.jdbcUrl")
                    ?: "jdbc:h2:mem:neogenesis;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                username = config.string("neogenesis.database.username") ?: "sa",
                password = config.string("neogenesis.database.password") ?: "",
                maximumPoolSize = config.string("neogenesis.database.maximumPoolSize")?.toIntOrNull() ?: 10,
                migrateOnStartup = config.bool("neogenesis.database.migrateOnStartup") ?: true
            )

            val mqttBrokerUrl = config.string("neogenesis.mqtt.brokerUrl")
            val mqtt = if (mqttBrokerUrl.isNullOrBlank()) {
                null
            } else {
                MqttConfig(
                    brokerUrl = mqttBrokerUrl,
                    clientId = config.string("neogenesis.mqtt.clientId") ?: "neogenesis-core-server",
                    topicRoot = config.string("neogenesis.mqtt.topicRoot") ?: "neogenesis/telemetry",
                    username = config.string("neogenesis.mqtt.username"),
                    password = config.string("neogenesis.mqtt.password")
                )
            }

            val clinicalConfig = ClinicalConfig(
                dicomweb = ClinicalConfig.DicomWebConfig(
                    enabled = config.bool("neogenesis.clinical.dicomweb.enabled") ?: false,
                    baseUrl = config.string("neogenesis.clinical.dicomweb.baseUrl"),
                    bearerToken = config.string("neogenesis.clinical.dicomweb.bearerToken"),
                    timeoutMs = config.string("neogenesis.clinical.dicomweb.timeoutMs")?.toLongOrNull() ?: 7_000L
                ),
                dimse = ClinicalConfig.DimseConfig(
                    enabled = config.bool("neogenesis.clinical.dimse.enabled") ?: false,
                    findScuPath = config.string("neogenesis.clinical.dimse.findScuPath"),
                    moveScuPath = config.string("neogenesis.clinical.dimse.moveScuPath"),
                    getScuPath = config.string("neogenesis.clinical.dimse.getScuPath"),
                    callingAeTitle = config.string("neogenesis.clinical.dimse.callingAeTitle") ?: "NEOGENESIS",
                    calledAeTitle = config.string("neogenesis.clinical.dimse.calledAeTitle") ?: "PACS",
                    remoteHost = config.string("neogenesis.clinical.dimse.remoteHost") ?: "127.0.0.1",
                    remotePort = config.string("neogenesis.clinical.dimse.remotePort")?.toIntOrNull() ?: 104,
                    localStorePath = config.string("neogenesis.clinical.dimse.localStorePath")
                ),
                hl7Mllp = ClinicalConfig.Hl7MllpConfig(
                    enabled = config.bool("neogenesis.clinical.hl7Mllp.enabled") ?: false,
                    host = config.string("neogenesis.clinical.hl7Mllp.host") ?: "0.0.0.0",
                    port = config.string("neogenesis.clinical.hl7Mllp.port")?.toIntOrNull() ?: 2575,
                    maxFrameBytes = config.string("neogenesis.clinical.hl7Mllp.maxFrameBytes")?.toIntOrNull() ?: 2_000_000,
                    connectTimeoutMs = config.string("neogenesis.clinical.hl7Mllp.connectTimeoutMs")?.toIntOrNull() ?: 3_000,
                    readTimeoutMs = config.string("neogenesis.clinical.hl7Mllp.readTimeoutMs")?.toIntOrNull() ?: 5_000
                ),
                validation = ClinicalConfig.ValidationConfig(
                    enforceStrict = config.bool("neogenesis.clinical.validation.enforceStrict") ?: true,
                    allowedFhirVersions = config.list("neogenesis.clinical.validation.allowedFhirVersions", setOf("R4", "4.0.1")),
                    requiredFhirProfiles = config.list("neogenesis.clinical.validation.requiredFhirProfiles", emptySet()),
                    requiredTerminologySystems = config.list(
                        "neogenesis.clinical.validation.requiredTerminologySystems",
                        setOf("http://loinc.org", "http://snomed.info/sct")
                    ),
                    allowedHl7Versions = config.list("neogenesis.clinical.validation.allowedHl7Versions", setOf("2.3", "2.4", "2.5", "2.5.1", "2.6")),
                    allowedHl7MessageTypes = config.list("neogenesis.clinical.validation.allowedHl7MessageTypes", setOf("ADT^A01", "ADT^A08", "ORM^O01", "ORU^R01"))
                )
            )

            val securityConfig = SecurityConfig(
                jwt = SecurityConfig.JwtConfig(
                    realm = config.string("neogenesis.security.jwt.realm") ?: "NeoGenesis",
                    issuer = config.string("neogenesis.security.jwt.issuer") ?: "neogenesis-auth",
                    audience = config.string("neogenesis.security.jwt.audience") ?: "neogenesis-api",
                    secret = config.string("neogenesis.security.jwt.secret")
                        ?: "replace-this-secret-before-production",
                    mode = config.string("neogenesis.security.jwt.mode") ?: "hmac",
                    jwksUrl = config.string("neogenesis.security.jwt.jwksUrl")
                ),
                mtls = SecurityConfig.MtlsConfig(
                    grpc = SecurityConfig.MtlsConfig.GrpcMtlsConfig(
                        enabled = config.bool("neogenesis.security.mtls.grpc.enabled") ?: false,
                        requireClientAuth = config.bool("neogenesis.security.mtls.grpc.requireClientAuth") ?: true,
                        certChainPath = config.string("neogenesis.security.mtls.grpc.certChainPath"),
                        privateKeyPath = config.string("neogenesis.security.mtls.grpc.privateKeyPath"),
                        trustCertPath = config.string("neogenesis.security.mtls.grpc.trustCertPath"),
                        hotReloadEnabled = config.bool("neogenesis.security.mtls.grpc.hotReloadEnabled") ?: true,
                        hotReloadIntervalMinutes = config.string("neogenesis.security.mtls.grpc.hotReloadIntervalMinutes")?.toLongOrNull()
                            ?: 60L
                    ),
                    mqtt = SecurityConfig.MtlsConfig.MqttMtlsConfig(
                        enabled = config.bool("neogenesis.security.mtls.mqtt.enabled") ?: false,
                        keyStorePath = config.string("neogenesis.security.mtls.mqtt.keyStorePath"),
                        keyStorePassword = config.string("neogenesis.security.mtls.mqtt.keyStorePassword"),
                        trustStorePath = config.string("neogenesis.security.mtls.mqtt.trustStorePath"),
                        trustStorePassword = config.string("neogenesis.security.mtls.mqtt.trustStorePassword")
                    ),
                    httpProxyValidation = SecurityConfig.MtlsConfig.HttpProxyValidationConfig(
                        enabled = config.bool("neogenesis.security.mtls.httpProxyValidation.enabled") ?: false,
                        verifyHeaderName = config.string("neogenesis.security.mtls.httpProxyValidation.verifyHeaderName")
                            ?: "x-ssl-client-verify",
                        verifySuccessValue = config.string("neogenesis.security.mtls.httpProxyValidation.verifySuccessValue")
                            ?: "SUCCESS"
                    )
                )
            )

            val secretsConfig = SecretsConfig(
                vaultEnabled = config.bool("neogenesis.secrets.vault.enabled") ?: false,
                vaultAddress = config.string("neogenesis.secrets.vault.address"),
                vaultToken = config.string("neogenesis.secrets.vault.token"),
                vaultMount = config.string("neogenesis.secrets.vault.mount") ?: "secret",
                vaultTimeoutMs = config.string("neogenesis.secrets.vault.timeoutMs")?.toLongOrNull() ?: 5_000L,
                kmsEnabled = config.bool("neogenesis.secrets.kms.enabled") ?: false,
                kmsRegion = config.string("neogenesis.secrets.kms.region"),
                kmsEndpointOverride = config.string("neogenesis.secrets.kms.endpointOverride")
            )

            val encryptionConfig = EncryptionConfig(
                enabled = config.bool("neogenesis.encryption.enabled") ?: false,
                phiKeyRef = config.string("neogenesis.encryption.phiKeyRef") ?: "env:NEOGENESIS_PHI_KEY_B64",
                piiKeyRef = config.string("neogenesis.encryption.piiKeyRef") ?: "env:NEOGENESIS_PII_KEY_B64"
            )

            val gdprConfig = GdprConfig(
                enforceConsent = config.bool("neogenesis.gdpr.enforceConsent") ?: false,
                defaultRetentionDays = config.string("neogenesis.gdpr.defaultRetentionDays")?.toIntOrNull() ?: 3650
            )

            val observabilityConfig = ObservabilityConfig(
                metricsPath = config.string("neogenesis.observability.metricsPath") ?: "/metrics",
                otlpEndpoint = config.string("neogenesis.observability.otlpEndpoint"),
                serviceName = config.string("neogenesis.observability.serviceName") ?: "neogenesis-core-server"
            )

            val resilienceConfig = ResilienceConfig(
                enabled = config.bool("neogenesis.resilience.enabled") ?: true,
                integrationTimeoutMs = config.string("neogenesis.resilience.integrationTimeoutMs")?.toLongOrNull() ?: 7_000L,
                circuitBreakerFailureThreshold = config.string("neogenesis.resilience.circuitBreakerFailureThreshold")?.toIntOrNull() ?: 5,
                circuitBreakerOpenStateMs = config.string("neogenesis.resilience.circuitBreakerOpenStateMs")?.toLongOrNull() ?: 30_000L,
                requireIdempotencyKey = config.bool("neogenesis.resilience.requireIdempotencyKey") ?: true,
                idempotencyTtlSeconds = config.string("neogenesis.resilience.idempotencyTtlSeconds")?.toLongOrNull() ?: 86_400L
            )

            val controlConfig = ControlConfig(
                latencyBudgetMs = config.string("neogenesis.control.latencyBudgetMs")?.toLongOrNull() ?: 50L
            )

            val serverlessConfig = ServerlessConfig(
                outboxBatchSize = config.string("neogenesis.serverless.outboxBatchSize")?.toIntOrNull() ?: 100,
                dispatchIntervalMs = config.string("neogenesis.serverless.dispatchIntervalMs")?.toLongOrNull() ?: 2_000L,
                provider = config.string("neogenesis.serverless.provider") ?: "logging",
                maxRetries = config.string("neogenesis.serverless.maxRetries")?.toIntOrNull() ?: 5,
                baseBackoffMs = config.string("neogenesis.serverless.baseBackoffMs")?.toLongOrNull() ?: 500L,
                maxBackoffMs = config.string("neogenesis.serverless.maxBackoffMs")?.toLongOrNull() ?: 30_000L,
                awsSqs = ServerlessConfig.AwsSqsConfig(
                    queueUrl = config.string("neogenesis.serverless.awsSqs.queueUrl"),
                    region = config.string("neogenesis.serverless.awsSqs.region") ?: "eu-west-1",
                    endpointOverride = config.string("neogenesis.serverless.awsSqs.endpointOverride")
                ),
                awsEventBridge = ServerlessConfig.AwsEventBridgeConfig(
                    eventBusName = config.string("neogenesis.serverless.awsEventBridge.eventBusName"),
                    sourceNamespace = config.string("neogenesis.serverless.awsEventBridge.sourceNamespace") ?: "com.neogenesis.core",
                    region = config.string("neogenesis.serverless.awsEventBridge.region") ?: "eu-west-1",
                    endpointOverride = config.string("neogenesis.serverless.awsEventBridge.endpointOverride")
                ),
                gcpPubSub = ServerlessConfig.GcpPubSubConfig(
                    projectId = config.string("neogenesis.serverless.gcpPubSub.projectId"),
                    topicId = config.string("neogenesis.serverless.gcpPubSub.topicId"),
                    emulatorHost = config.string("neogenesis.serverless.gcpPubSub.emulatorHost")
                )
            )

            return AppConfig(
                runtime = runtimeConfig,
                grpcPort = grpcPort,
                database = databaseConfig,
                mqtt = mqtt,
                clinical = clinicalConfig,
                security = securityConfig,
                secrets = secretsConfig,
                encryption = encryptionConfig,
                gdpr = gdprConfig,
                observability = observabilityConfig,
                resilience = resilienceConfig,
                control = controlConfig,
                serverless = serverlessConfig
            )
        }
    }
}

private fun ApplicationConfig.string(path: String): String? =
    propertyOrNull(path)?.getString()

private fun ApplicationConfig.bool(path: String): Boolean? =
    propertyOrNull(path)?.getString()?.trim()?.lowercase()?.let {
        when (it) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

private fun ApplicationConfig.list(path: String, default: Set<String>): Set<String> {
    val raw = propertyOrNull(path)?.getString() ?: return default
    return raw.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
        .ifEmpty { default }
}
