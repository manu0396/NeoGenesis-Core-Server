package com.neogenesis.server.infrastructure.clinical

import com.neogenesis.server.infrastructure.config.AppConfig
import java.io.File
import java.time.Duration

class DimseCommandClient(
    private val config: AppConfig.ClinicalConfig.DimseConfig
) {

    fun cFind(queryKeys: Map<String, String>, returnKeys: List<String> = listOf("StudyInstanceUID")): String {
        val binary = requireNotNull(config.findScuPath) { "findscu path is required" }
        val args = mutableListOf(
            binary,
            "-c", "${config.calledAeTitle}@${config.remoteHost}:${config.remotePort}",
            "-b", config.callingAeTitle,
            "-L", "STUDY"
        )
        returnKeys.forEach { args += listOf("-r", it) }
        queryKeys.forEach { (k, v) -> args += listOf("-m", "$k=$v") }
        return execute(args)
    }

    fun cMove(studyInstanceUid: String, destinationAeTitle: String): String {
        val binary = requireNotNull(config.moveScuPath) { "movescu path is required" }
        val args = mutableListOf(
            binary,
            "-c", "${config.calledAeTitle}@${config.remoteHost}:${config.remotePort}",
            "-b", config.callingAeTitle,
            "--dest", destinationAeTitle,
            "-L", "STUDY",
            "-m", "StudyInstanceUID=$studyInstanceUid"
        )
        return execute(args)
    }

    fun cGet(studyInstanceUid: String): String {
        val binary = requireNotNull(config.getScuPath) { "getscu path is required" }
        val args = mutableListOf(
            binary,
            "-c", "${config.calledAeTitle}@${config.remoteHost}:${config.remotePort}",
            "-b", config.callingAeTitle,
            "-L", "STUDY",
            "-m", "StudyInstanceUID=$studyInstanceUid"
        )
        if (!config.localStorePath.isNullOrBlank()) {
            args += listOf("--directory", config.localStorePath)
            File(config.localStorePath).mkdirs()
        }
        return execute(args)
    }

    private fun execute(command: List<String>): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val completed = process.waitFor(Duration.ofSeconds(60).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            error("DIMSE command timeout: ${command.firstOrNull()}")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.exitValue() != 0) {
            error("DIMSE command failed (code=${process.exitValue()}): $output")
        }
        return output
    }
}
