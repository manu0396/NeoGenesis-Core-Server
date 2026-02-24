package com.neogenesis.gateway

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class BackpressureLimiter(
    maxPerSecond: Int,
) {
    private val permits = Semaphore(maxPerSecond, true)
    private val refillThread =
        Thread {
            while (true) {
                val missing = maxPerSecond - permits.availablePermits()
                if (missing > 0) {
                    permits.release(missing)
                }
                Thread.sleep(1000)
            }
        }.apply {
            isDaemon = true
            name = "gateway-backpressure"
            start()
        }

    fun acquire(batchSize: Int, timeoutMs: Long): Boolean {
        return permits.tryAcquire(batchSize, timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        refillThread.interrupt()
    }
}
