package com.neogenesis.server.infrastructure.clinical

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class Hl7MllpClient {
    fun send(
        host: String,
        port: Int,
        message: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs

            val output = socket.getOutputStream()
            output.write(START_BLOCK.toInt())
            output.write(message.toByteArray(StandardCharsets.UTF_8))
            output.write(END_BLOCK.toInt())
            output.write(CARRIAGE_RETURN.toInt())
            output.flush()

            return readFrame(socket)
        }
    }

    private fun readFrame(socket: Socket): String {
        val input = socket.getInputStream()
        var current = input.read()
        while (current != -1 && current != START_BLOCK.toInt()) {
            current = input.read()
        }
        require(current == START_BLOCK.toInt()) { "MLLP ACK start block not found" }

        val buffer = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            require(next != -1) { "Unexpected EOF while reading MLLP ACK frame" }
            if (next == END_BLOCK.toInt()) {
                val tail = input.read()
                require(tail == CARRIAGE_RETURN.toInt()) { "MLLP ACK missing carriage return terminator" }
                break
            }
            buffer.write(next)
        }
        return buffer.toString(StandardCharsets.UTF_8)
    }

    companion object {
        private const val START_BLOCK: Byte = 0x0B
        private const val END_BLOCK: Byte = 0x1C
        private const val CARRIAGE_RETURN: Byte = 0x0D
    }
}
