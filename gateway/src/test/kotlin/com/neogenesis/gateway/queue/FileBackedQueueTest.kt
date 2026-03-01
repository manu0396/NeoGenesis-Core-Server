package com.neogenesis.gateway.queue

import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files

class FileBackedQueueTest {
    @Test
    fun `queue persists across reloads`() {
        val dir = Files.createTempDirectory("gateway-queue-test")
        val queue = FileBackedQueue(dir, 1_000_000)
        val item = QueueItem(
            id = "id-1",
            type = QueueItemType.TELEMETRY,
            payload = "{\"metricKey\":\"x\"}",
            createdAtMs = 1,
        )
        queue.append(item)
        assertEquals(1, queue.size())

        val reloaded = FileBackedQueue(dir, 1_000_000)
        assertEquals(1, reloaded.size())
        assertEquals("id-1", reloaded.peek(1).first().id)
    }
}
