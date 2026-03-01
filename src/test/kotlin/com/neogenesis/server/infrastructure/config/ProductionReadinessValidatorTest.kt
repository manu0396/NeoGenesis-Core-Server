package com.neogenesis.server.infrastructure.config

import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ProductionReadinessValidatorTest {
    @Test
    fun allows_development_defaults() {
        val config = baseConfig()
        ProductionReadinessValidator.validate(
            config = config,
            resolvedJwtSecret = "replace-this-secret-before-production",
            resolvedPhiKey = null,
            resolvedPiiKey = null,
        )
    }

    @Test
    fun rejects_unsafe_production_profile() {
        val config =
            baseConfig(
                runtime = AppConfig.RuntimeConfig(environment = "production"),
            )

        assertFailsWith<IllegalStateException> {
            ProductionReadinessValidator.validate(
                config = config,
                resolvedJwtSecret = "replace-this-secret-before-production",
                resolvedPhiKey = null,
                resolvedPiiKey = null,
            )
        }
    }

    @Test
    fun accepts_hardened_production_profile() {
        val cert = createTempFile("grpc-cert", ".pem").toFile()
        val key = createTempFile("grpc-key", ".pem").toFile()
        val trust = createTempFile("grpc-ca", ".pem").toFile()

        val config =
            baseConfig(
                runtime = AppConfig.RuntimeConfig(environment = "production"),
                database =
                    AppConfig.DatabaseConfig(
                        jdbcUrl = "jdbc:postgresql://localhost:5432/neogenesis",
                        username = "neo",
                        password = "secret",
                        maximumPoolSize = 10,
                        migrateOnStartup = true,
                        connectionTimeoutMs = 3_000,
                        validationTimeoutMs = 1_000,
                        idleTimeoutMs = 600_000,
                        maxLifetimeMs = 1_800_000,
                    ),
                encryption =
                    AppConfig.EncryptionConfig(
                        enabled = true,
                        phiKeyRef = "env:NEOGENESIS_PHI_KEY_B64",
                        piiKeyRef = "env:NEOGENESIS_PII_KEY_B64",
                    ),
                security =
                    baseConfig().security.copy(
                        mtls =
                            baseConfig().security.mtls.copy(
                                grpc =
                                    AppConfig.SecurityConfig.MtlsConfig.GrpcMtlsConfig(
                                        enabled = true,
                                        requireClientAuth = true,
                                        certChainPath = cert.absolutePath,
                                        privateKeyPath = key.absolutePath,
                                        trustCertPath = trust.absolutePath,
                                        hotReloadEnabled = true,
                                        hotReloadIntervalMinutes = 60,
                                    ),
                            ),
                    ),
                serverless = baseConfig().serverless.copy(provider = "aws_eventbridge"),
            )

        ProductionReadinessValidator.validate(
            config = config,
            resolvedJwtSecret = "this-is-a-long-production-secret-123456",
            resolvedPhiKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            resolvedPiiKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
        )
    }

    private fun baseConfig(
        runtime: AppConfig.RuntimeConfig = AppConfig.RuntimeConfig(environment = "development"),
        database: AppConfig.DatabaseConfig =
            AppConfig.DatabaseConfig(
                jdbcUrl = "jdbc:h2:mem:neogenesis;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                username = "sa",
                password = "",
                maximumPoolSize = 10,
                migrateOnStartup = true,
                connectionTimeoutMs = 3_000,
                validationTimeoutMs = 1_000,
                idleTimeoutMs = 600_000,
                maxLifetimeMs = 1_800_000,
            ),
        security: AppConfig.SecurityConfig =
            AppConfig.SecurityConfig(
                jwt =
                    AppConfig.SecurityConfig.JwtConfig(
                        realm = "NeoGenesis",
                        issuer = "neogenesis-auth",
                        audience = "neogenesis-api",
                        secret = "replace-this-secret-before-production",
                        mode = "hmac",
                        jwksUrl = null,
                    ),
                mtls =
                    AppConfig.SecurityConfig.MtlsConfig(
                        grpc =
                            AppConfig.SecurityConfig.MtlsConfig.GrpcMtlsConfig(
                                enabled = false,
                                requireClientAuth = true,
                                certChainPath = null,
                                privateKeyPath = null,
                                trustCertPath = null,
                                hotReloadEnabled = true,
                                hotReloadIntervalMinutes = 60,
                            ),
                        mqtt =
                            AppConfig.SecurityConfig.MtlsConfig.MqttMtlsConfig(
                                enabled = false,
                                keyStorePath = null,
                                keyStorePassword = null,
                                trustStorePath = null,
                                trustStorePassword = null,
                            ),
                        httpProxyValidation =
                            AppConfig.SecurityConfig.MtlsConfig.HttpProxyValidationConfig(
                                enabled = false,
                                verifyHeaderName = "x-ssl-client-verify",
                                verifySuccessValue = "SUCCESS",
                            ),
                    ),
            ),
        encryption: AppConfig.EncryptionConfig =
            AppConfig.EncryptionConfig(
                enabled = false,
                phiKeyRef = "env:NEOGENESIS_PHI_KEY_B64",
                piiKeyRef = "env:NEOGENESIS_PII_KEY_B64",
            ),
        serverless: AppConfig.ServerlessConfig =
            AppConfig.ServerlessConfig(
                outboxBatchSize = 100,
                dispatchIntervalMs = 2000,
                provider = "logging",
                maxRetries = 5,
                baseBackoffMs = 500,
                maxBackoffMs = 30_000,
                awsSqs =
                    AppConfig.ServerlessConfig.AwsSqsConfig(
                        queueUrl = null,
                        region = "eu-west-1",
                        endpointOverride = null,
                    ),
                awsEventBridge =
                    AppConfig.ServerlessConfig.AwsEventBridgeConfig(
                        eventBusName = "neogenesis-bus",
                        sourceNamespace = "com.neogenesis.core",
                        region = "eu-west-1",
                        endpointOverride = null,
                    ),
                gcpPubSub =
                    AppConfig.ServerlessConfig.GcpPubSubConfig(
                        projectId = null,
                        topicId = null,
                        emulatorHost = null,
                    ),
            ),
        billing: AppConfig.BillingConfig =
            AppConfig.BillingConfig(
                enabled = false,
                provider = "fake",
                freePlanId = "free",
                proPlanId = "pro",
                freePlanFeatures = emptySet(),
                proPlanFeatures = setOf("audit:evidence_export", "compliance:traceability_audit"),
                stripe =
                    AppConfig.BillingConfig.StripeConfig(
                        secretKey = null,
                        webhookSecret = null,
                        priceIdFree = null,
                        priceIdPro = null,
                        successUrl = "https://example.com/billing/success",
                        cancelUrl = "https://example.com/billing/cancel",
                        portalReturnUrl = "https://example.com/billing/portal",
                    ),
            ),
    ): AppConfig {
        return AppConfig(
            runtime = runtime,
            grpcPort = 50051,
            database = database,
            mqtt = null,
            clinical =
                AppConfig.ClinicalConfig(
                    dicomweb =
                        AppConfig.ClinicalConfig.DicomWebConfig(
                            enabled = false,
                            baseUrl = null,
                            bearerToken = null,
                            timeoutMs = 7000,
                        ),
                    dimse =
                        AppConfig.ClinicalConfig.DimseConfig(
                            enabled = false,
                            findScuPath = null,
                            moveScuPath = null,
                            getScuPath = null,
                            callingAeTitle = "NEOGENESIS",
                            calledAeTitle = "PACS",
                            remoteHost = "127.0.0.1",
                            remotePort = 104,
                            localStorePath = null,
                        ),
                    hl7Mllp =
                        AppConfig.ClinicalConfig.Hl7MllpConfig(
                            enabled = false,
                            host = "0.0.0.0",
                            port = 2575,
                            maxFrameBytes = 2_000_000,
                            connectTimeoutMs = 3000,
                            readTimeoutMs = 5000,
                        ),
                    validation =
                        AppConfig.ClinicalConfig.ValidationConfig(
                            enforceStrict = true,
                            allowedFhirVersions = setOf("R4", "4.0.1"),
                            requiredFhirProfiles = emptySet(),
                            requiredTerminologySystems = setOf("http://loinc.org", "http://snomed.info/sct"),
                            allowedHl7Versions = setOf("2.3", "2.4", "2.5", "2.5.1", "2.6"),
                            allowedHl7MessageTypes = setOf("ADT^A01", "ADT^A08", "ORM^O01", "ORU^R01"),
                        ),
                ),
            security = security,
            secrets =
                AppConfig.SecretsConfig(
                    vaultEnabled = false,
                    vaultAddress = null,
                    vaultToken = null,
                    vaultMount = "secret",
                    vaultTimeoutMs = 5000,
                    kmsEnabled = false,
                    kmsRegion = null,
                    kmsEndpointOverride = null,
                ),
            encryption = encryption,
            gdpr =
                AppConfig.GdprConfig(
                    enforceConsent = false,
                    defaultRetentionDays = 3650,
                ),
            observability =
                AppConfig.ObservabilityConfig(
                    metricsPath = "/metrics",
                    otlpEndpoint = null,
                    serviceName = "neogenesis-core-server",
                ),
            resilience =
                AppConfig.ResilienceConfig(
                    enabled = true,
                    integrationTimeoutMs = 7000,
                    circuitBreakerFailureThreshold = 5,
                    circuitBreakerOpenStateMs = 30_000,
                    requireIdempotencyKey = true,
                    idempotencyTtlSeconds = 86_400,
                ),
            control =
                AppConfig.ControlConfig(
                    latencyBudgetMs = 50,
                ),
            serverless = serverless,
            billing = billing,
        )
    }
}
