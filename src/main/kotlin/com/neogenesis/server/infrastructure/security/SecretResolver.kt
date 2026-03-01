package com.neogenesis.server.infrastructure.security

import com.neogenesis.server.infrastructure.config.AppConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

class SecretResolver(
    private val config: AppConfig.SecretsConfig
) {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.vaultTimeoutMs))
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val kmsClient: KmsClient? = if (config.kmsEnabled) {
        val builder = KmsClient.builder()
        if (!config.kmsRegion.isNullOrBlank()) {
            builder.region(Region.of(config.kmsRegion))
        }
        if (!config.kmsEndpointOverride.isNullOrBlank()) {
            builder.endpointOverride(URI.create(config.kmsEndpointOverride))
        }
        builder.build()
    } else {
        null
    }

    fun resolve(refOrValue: String?): String? {
        if (refOrValue.isNullOrBlank()) {
            return null
        }
        return when {
            refOrValue.startsWith("env:", ignoreCase = true) -> {
                val envName = refOrValue.substringAfter(':').trim()
                System.getenv(envName)
            }
            refOrValue.startsWith("vault:", ignoreCase = true) -> {
                resolveVault(refOrValue.substringAfter(':'))
            }
            refOrValue.startsWith("kms:", ignoreCase = true) -> {
                resolveKms(refOrValue.substringAfter(':').trim())
            }
            else -> refOrValue
        }
    }

    private fun resolveVault(pathAndField: String): String? {
        require(config.vaultEnabled) { "Vault is disabled but a vault: secret reference was requested." }
        require(!config.vaultAddress.isNullOrBlank()) { "Vault address is missing." }
        require(!config.vaultToken.isNullOrBlank()) { "Vault token is missing." }

        val path = pathAndField.substringBefore('#').trim().trimStart('/')
        val field = pathAndField.substringAfter('#', "value").trim()
        require(path.isNotBlank()) { "Vault path must not be empty." }

        val uri = URI.create("${config.vaultAddress.trimEnd('/')}/v1/${config.vaultMount.trim('/')}/data/$path")
        val request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofMillis(config.vaultTimeoutMs))
            .header("X-Vault-Token", config.vaultToken)
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("Vault secret resolution failed with status=${response.statusCode()} path=$path")
        }

        val root = json.parseToJsonElement(response.body()).jsonObject
        val data = root["data"]?.jsonObject?.get("data")?.jsonObject
            ?: error("Invalid Vault KV response shape for path=$path")
        return data[field]?.jsonPrimitive?.content
    }

    private fun resolveKms(cipherTextB64: String): String? {
        require(config.kmsEnabled) { "KMS is disabled but a kms: secret reference was requested." }
        val kms = requireNotNull(kmsClient) { "KMS client is not initialized." }
        require(cipherTextB64.isNotBlank()) { "KMS ciphertext reference is empty." }

        val cipherBytes = runCatching { Base64.getDecoder().decode(cipherTextB64) }
            .getOrElse { error("KMS ciphertext must be Base64") }
        val response = kms.decrypt(
            DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(cipherBytes))
                .build()
        )
        return response.plaintext()?.asUtf8String()
    }
}
