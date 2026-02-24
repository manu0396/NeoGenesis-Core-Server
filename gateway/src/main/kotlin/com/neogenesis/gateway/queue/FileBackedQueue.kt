package com.neogenesis.gateway.queue

import java.io.BufferedReader
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

class FileBackedQueue(
    private val root: Path,
    private val maxBytes: Long,
) : OfflineQueue {
    private val dataFile = root.resolve("queue.log")
    private val ackFile = root.resolve("queue.ack")
    private val inMemory = ConcurrentLinkedQueue<QueueItem>()
    private val sizeCounter = AtomicLong(0)

    init {
        Files.createDirectories(root)
        if (Files.exists(dataFile)) {
            loadFromDisk()
        }
    }

    override fun append(item: QueueItem) {
        appendAll(listOf(item))
    }

    override fun appendAll(items: List<QueueItem>) {
        if (items.isEmpty()) return
        val serialized = items.joinToString(separator = "\n") { serialize(it) } + "\n"
        Files.newBufferedWriter(dataFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { writer ->
            writer.write(serialized)
        }
        items.forEach { inMemory.add(it) }
        sizeCounter.addAndGet(items.size.toLong())
        enforceMaxBytes()
    }

    override fun peek(limit: Int): List<QueueItem> {
        if (limit <= 0) return emptyList()
        return inMemory.take(limit)
    }

    override fun ack(ids: List<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        val remaining = inMemory.filterNot { idSet.contains(it.id) }
        inMemory.clear()
        remaining.forEach { inMemory.add(it) }
        sizeCounter.set(remaining.size.toLong())
        writeAckSnapshot(remaining)
    }

    override fun size(): Int = sizeCounter.get().toInt()

    private fun loadFromDisk() {
        val items = mutableListOf<QueueItem>()
        Files.newBufferedReader(dataFile).use { reader ->
            readItems(reader, items)
        }
        if (Files.exists(ackFile)) {
            val acked = mutableSetOf<String>()
            Files.newBufferedReader(ackFile).use { reader ->
                reader.lineSequence().filter { it.isNotBlank() }.forEach { acked.add(it.trim()) }
            }
            items.filterNot { acked.contains(it.id) }.forEach { inMemory.add(it) }
        } else {
            items.forEach { inMemory.add(it) }
        }
        sizeCounter.set(inMemory.size.toLong())
    }

    private fun readItems(reader: BufferedReader, items: MutableList<QueueItem>) {
        reader.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            deserialize(line)?.let(items::add)
        }
    }

    private fun writeAckSnapshot(remaining: List<QueueItem>) {
        Files.newBufferedWriter(ackFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { writer ->
            remaining.forEach { writer.write(it.id + "\n") }
        }
    }

    private fun serialize(item: QueueItem): String {
        return listOf(item.id, item.type.name, item.createdAtMs.toString(), item.payload).joinToString("|")
    }

    private fun deserialize(line: String): QueueItem? {
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return null
        return QueueItem(
            id = parts[0],
            type = runCatching { QueueItemType.valueOf(parts[1]) }.getOrNull() ?: return null,
            createdAtMs = parts[2].toLongOrNull() ?: return null,
            payload = parts[3],
        )
    }

    private fun enforceMaxBytes() {
        val size = Files.size(dataFile)
        if (size <= maxBytes) return
        // Best-effort compaction: rewrite file with current in-memory items.
        Files.newBufferedWriter(dataFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { writer ->
            inMemory.forEach { writer.write(serialize(it) + "\n") }
        }
        if (Files.size(dataFile) > maxBytes) {
            // If still too large, drop oldest items.
            while (Files.size(dataFile) > maxBytes && inMemory.isNotEmpty()) {
                inMemory.poll()
                sizeCounter.decrementAndGet()
                Files.newBufferedWriter(dataFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { writer ->
                    inMemory.forEach { writer.write(serialize(it) + "\n") }
                }
            }
        }
    }
}
