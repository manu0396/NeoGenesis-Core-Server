package com.neogenesis.server.infrastructure.config

import java.io.File

object ProductionReadinessValidator {
    private const val DEFAULT_JWT_SECRET = "replace-this-secret-before-production"

    fun validate(
        config: AppConfig,
        resolvedJwtSecret: String?,
        resolvedPhiKey: String?,
        resolvedPiiKey: String?
    ) {
        val violations = mutableListOf<String>()

        val provider = config.serverless.provider.lowercase()
        val allowedProviders = setOf("logging", "sqs", "aws_sqs", "eventbridge", "aws_eventbridge", "pubsub", "gcp_pubsub")
        if (provider !in allowedProviders) {
            violations += "Unsupported serverless provider: ${config.serverless.provider}"
        }

        if (!config.runtime.isProduction()) {
            if (violations.isNotEmpty()) {
                throw IllegalStateException("Invalid configuration: ${violations.joinToString("; ")}")
            }
            return
        }

        if (config.database.jdbcUrl.startsWith("jdbc:h2:mem:", ignoreCase = true)) {
            violations += "Production cannot use in-memory H2 database."
        }
        if (provider == "logging") {
            violations += "Production cannot use logging outbox provider."
        }
        if (!config.encryption.enabled) {
            violations += "Production requires encryption at rest (neogenesis.encryption.enabled=true)."
        } else {
            if (resolvedPhiKey.isNullOrBlank()) {
                violations += "PHI encryption key is missing in production."
            }
            if (resolvedPiiKey.isNullOrBlank()) {
                violations += "PII encryption key is missing in production."
            }
        }

        val jwtMode = config.security.jwt.mode.lowercase()
        if (jwtMode == "hmac") {
            if (resolvedJwtSecret.isNullOrBlank()) {
                violations += "JWT secret cannot be empty in production."
            } else {
                if (resolvedJwtSecret == DEFAULT_JWT_SECRET) {
                    violations += "Default JWT secret is not allowed in production."
                }
                if (resolvedJwtSecret.length < 32) {
                    violations += "JWT secret must be at least 32 characters in production."
                }
            }
        } else if (jwtMode == "oidc") {
            if (config.security.jwt.jwksUrl.isNullOrBlank()) {
                violations += "OIDC mode requires neogenesis.security.jwt.jwksUrl in production."
            }
        } else {
            violations += "Unsupported JWT mode: ${config.security.jwt.mode}"
        }

        val grpcMtls = config.security.mtls.grpc
        if (!grpcMtls.enabled) {
            violations += "Production requires gRPC mTLS enabled."
        } else {
            if (grpcMtls.certChainPath.isNullOrBlank() || grpcMtls.privateKeyPath.isNullOrBlank()) {
                violations += "gRPC mTLS certChainPath/privateKeyPath are required in production."
            }
            if (grpcMtls.requireClientAuth && grpcMtls.trustCertPath.isNullOrBlank()) {
                violations += "gRPC mTLS trustCertPath is required when requireClientAuth=true."
            }
            validateFile("gRPC certChainPath", grpcMtls.certChainPath, violations)
            validateFile("gRPC privateKeyPath", grpcMtls.privateKeyPath, violations)
            if (grpcMtls.requireClientAuth) {
                validateFile("gRPC trustCertPath", grpcMtls.trustCertPath, violations)
            }
        }

        if (violations.isNotEmpty()) {
            throw IllegalStateException(
                "Production readiness checks failed: ${violations.joinToString("; ")}"
            )
        }
    }

    private fun validateFile(label: String, path: String?, violations: MutableList<String>) {
        if (path.isNullOrBlank()) {
            return
        }
        if (!File(path).exists()) {
            violations += "$label does not exist on disk: $path"
        }
    }
}

private fun AppConfig.RuntimeConfig.isProduction(): Boolean {
    val normalized = environment.trim().lowercase()
    return normalized == "prod" || normalized == "production"
}
