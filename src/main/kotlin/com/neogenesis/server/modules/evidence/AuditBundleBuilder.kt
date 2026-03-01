package com.neogenesis.server.modules.evidence

import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.TelemetryRepository
import com.neogenesis.server.infrastructure.persistence.TwinMetricsRepository
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class AuditBundle(
    val bytes: ByteArray,
    val manifest: AuditBundleManifest,
)

data class AuditBundleManifest(
    val jobId: String,
    val tenantId: String,
    val generatedAt: String,
    val serverVersion: String,
    val files: List<BundleFileHash>,
    val bundleHash: String,
    val hashAlgorithm: String,
)

data class BundleFileHash(
    val path: String,
    val sha256: String,
)

object AuditBundleBuilder {
    fun build(
        jobId: String,
        tenantId: String,
        telemetryRepository: TelemetryRepository,
        twinMetricsRepository: TwinMetricsRepository,
        auditLogRepository: AuditLogRepository,
        serverVersion: String,
    ): AuditBundle {
        val telemetry = telemetryRepository.listByJob(jobId, null, null, 5_000)
        val twin = twinMetricsRepository.listByJob(jobId, null, null, 5_000)
        val audit = auditLogRepository.listByJob(jobId, 10_000)

        val files = LinkedHashMap<String, ByteArray>()
        files["telemetry.csv"] = buildTelemetryCsv(telemetry).toByteArray(Charsets.UTF_8)
        files["twin.csv"] = buildTwinCsv(twin).toByteArray(Charsets.UTF_8)
        files["audit.csv"] = buildAuditCsv(audit).toByteArray(Charsets.UTF_8)

        val fileHashes =
            files.map { (name, content) ->
                BundleFileHash(path = name, sha256 = sha256(content))
            }

        val chain = computeHashChain(fileHashes)
        val manifest =
            AuditBundleManifest(
                jobId = jobId,
                tenantId = tenantId,
                generatedAt = Instant.now().toString(),
                serverVersion = serverVersion,
                files = fileHashes,
                bundleHash = chain.lastHash,
                hashAlgorithm = "SHA-256",
            )

        val zipBytes = buildZip(files, manifest, chain)
        return AuditBundle(bytes = zipBytes, manifest = manifest)
    }

    private fun buildZip(
        files: Map<String, ByteArray>,
        manifest: AuditBundleManifest,
        chain: HashChain,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
            val manifestJson = manifest.toJson()
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val chainJson = chain.toJson()
            zip.putNextEntry(ZipEntry("hash-chain.json"))
            zip.write(chainJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun buildTelemetryCsv(records: List<com.neogenesis.server.infrastructure.persistence.TelemetryRecord>): String {
        val lines = mutableListOf("id,recorded_at,payload_json")
        records.forEach { entry ->
            lines += "${entry.id},${entry.recordedAt},${entry.payloadJson}"
        }
        return lines.joinToString("\n")
    }

    private fun buildTwinCsv(records: List<com.neogenesis.server.infrastructure.persistence.TwinMetricRecord>): String {
        val lines = mutableListOf("id,recorded_at,payload_json")
        records.forEach { entry ->
            lines += "${entry.id},${entry.recordedAt},${entry.payloadJson}"
        }
        return lines.joinToString("\n")
    }

    private fun buildAuditCsv(records: List<com.neogenesis.server.infrastructure.persistence.AuditLogRecord>): String {
        val lines = mutableListOf("id,event_type,created_at,payload_json")
        records.forEach { entry ->
            lines += "${entry.id},${entry.eventType},${entry.createdAt},${entry.payloadJson}"
        }
        return lines.joinToString("\n")
    }

    private fun sha256(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(content).joinToString("") { "%02x".format(it) }
    }

    private data class HashChain(
        val entries: List<HashChainEntry>,
        val lastHash: String,
    )

    private data class HashChainEntry(
        val path: String,
        val fileHash: String,
        val previousHash: String,
        val chainHash: String,
    )

    private fun computeHashChain(files: List<BundleFileHash>): HashChain {
        val entries = mutableListOf<HashChainEntry>()
        var previous = "GENESIS"
        files.forEach { file ->
            val payload = "${file.path}|${file.sha256}|$previous"
            val chainHash = sha256(payload.toByteArray(Charsets.UTF_8))
            entries +=
                HashChainEntry(
                    path = file.path,
                    fileHash = file.sha256,
                    previousHash = previous,
                    chainHash = chainHash,
                )
            previous = chainHash
        }
        return HashChain(entries = entries, lastHash = previous)
    }

    private fun HashChain.toJson(): String {
        val items =
            entries.joinToString(",") {
                """{"path":"${it.path}","fileHash":"${it.fileHash}","previousHash":"${it.previousHash}","chainHash":"${it.chainHash}"}"""
            }
        return buildString {
            appendLine("{")
            appendLine("  \"entries\": [$items],")
            appendLine("  \"lastHash\": \"$lastHash\"")
            append("}")
        }
    }

    private fun AuditBundleManifest.toJson(): String {
        val fileEntries =
            files.joinToString(",") { """{"path":"${it.path}","sha256":"${it.sha256}"}""" }
        return buildString {
            appendLine("{")
            appendLine("  \"jobId\": \"$jobId\",")
            appendLine("  \"tenantId\": \"$tenantId\",")
            appendLine("  \"generatedAt\": \"$generatedAt\",")
            appendLine("  \"serverVersion\": \"$serverVersion\",")
            appendLine("  \"files\": [$fileEntries],")
            appendLine("  \"bundleHash\": \"$bundleHash\",")
            appendLine("  \"hashAlgorithm\": \"$hashAlgorithm\"")
            append("}")
        }
    }
}
