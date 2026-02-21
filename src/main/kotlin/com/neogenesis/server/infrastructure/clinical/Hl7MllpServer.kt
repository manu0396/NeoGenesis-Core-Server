package com.neogenesis.server.infrastructure.clinical

import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class Hl7MllpServer(
    private val host: String,
    private val port: Int,
    private val maxFrameBytes: Int,
    private val onMessage: (String) -> String
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(Hl7MllpServer::class.java)
    private val running = AtomicBoolean(false)
    private val workerPool: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        val socket = ServerSocket(port, 100, InetAddress.getByName(host))
        serverSocket = socket
        workerPool.submit {
            logger.info("HL7 MLLP listener started on {}:{}", host, port)
            while (running.get()) {
                try {
                    val client = socket.accept()
                    workerPool.submit { handleClient(client) }
                } catch (error: Exception) {
                    if (running.get()) {
                        logger.error("HL7 MLLP accept loop failure", error)
                    }
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val input = client.getInputStream()
            val output = client.getOutputStream()
            while (running.get()) {
                val frame = readFrame(input = input) ?: break
                val response = runCatching { onMessage(frame) }
                    .getOrElse { error ->
                        logger.error("HL7 MLLP frame processing failed", error)
                        buildNack(frame, error.message ?: "processing_error")
                    }
                sendFrame(output = output, payload = response)
            }
        }
    }

    private fun readFrame(input: java.io.InputStream): String? {
        var current = input.read()
        while (current != -1 && current != START_BLOCK.toInt()) {
            current = input.read()
        }
        if (current == -1) {
            return null
        }

        val buffer = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next == -1) {
                return null
            }
            if (next == END_BLOCK.toInt()) {
                input.read() // consume CR
                break
            }
            if (buffer.size() >= maxFrameBytes) {
                throw IllegalStateException("MLLP frame exceeds maxFrameBytes=$maxFrameBytes")
            }
            buffer.write(next)
        }
        return buffer.toString(StandardCharsets.UTF_8)
    }

    private fun sendFrame(output: java.io.OutputStream, payload: String) {
        output.write(START_BLOCK.toInt())
        output.write(payload.toByteArray(StandardCharsets.UTF_8))
        output.write(END_BLOCK.toInt())
        output.write(CARRIAGE_RETURN.toInt())
        output.flush()
    }

    private fun buildNack(message: String, error: String): String {
        val controlId = extractControlId(message)
        val timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .format(java.time.LocalDateTime.now())
        return "MSH|^~\\&|NeoGenesisCore|NeoGenesis|Unknown|Unknown|$timestamp||ACK|${java.util.UUID.randomUUID()}|P|2.5\r" +
            "MSA|AE|$controlId|$error\r"
    }

    private fun extractControlId(message: String): String {
        val msh = message.split('\r', '\n')
            .firstOrNull { it.startsWith("MSH|") }
            ?.split('|')
            .orEmpty()
        return msh.getOrNull(9) ?: "UNKNOWN"
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        workerPool.shutdownNow()
    }

    companion object {
        private const val START_BLOCK: Byte = 0x0B
        private const val END_BLOCK: Byte = 0x1C
        private const val CARRIAGE_RETURN: Byte = 0x0D
    }
}
