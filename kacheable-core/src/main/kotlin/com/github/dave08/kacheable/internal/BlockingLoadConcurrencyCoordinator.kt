package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheLoadRejectedException
import com.github.dave08.kacheable.CacheLoadRole
import com.github.dave08.kacheable.CacheWaitReason
import com.github.dave08.kacheable.LoadConcurrencyConfig
import com.github.dave08.kacheable.LoadConcurrencyGroup
import com.github.dave08.kacheable.LoadConcurrencySettings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class BlockingLoadConcurrencyCoordinator(
    private val settings: LoadConcurrencySettings,
) {
    private val limiters = ConcurrentHashMap<String, BlockingLoadLimiter>()
    private val declaredGroupDefaults = ConcurrentHashMap<String, LoadConcurrencyConfig>()

    fun <R> withPermit(
        cacheName: String,
        group: LoadConcurrencyGroup?,
        observation: OperationObservation,
        block: () -> R,
    ): R {
        val resolved = resolve(cacheName, group) ?: return block()
        val limiter = limiters.compute(resolved.first) { _, current ->
            (current ?: BlockingLoadLimiter(resolved.first, resolved.second)).also {
                require(it.config == resolved.second) {
                    "Load concurrency '${resolved.first}' was resolved with conflicting configurations."
                }
            }
        }!!
        val permit = limiter.acquire(observation)
        try {
            return block()
        } finally {
            permit.release()
        }
    }

    private fun resolve(
        cacheName: String,
        group: LoadConcurrencyGroup?,
    ): Pair<String, LoadConcurrencyConfig>? {
        if (group != null) {
            val previous = declaredGroupDefaults.putIfAbsent(group.name, group.defaults)
            require(previous == null || previous == group.defaults) {
                "Load concurrency group '${group.name}' was declared with conflicting defaults."
            }
            return "group:${group.name}" to (settings.overrides[group] ?: group.defaults)
        }
        return settings.default?.let { "cache:$cacheName" to it }
    }

    private class BlockingLoadLimiter(
        private val name: String,
        val config: LoadConcurrencyConfig,
    ) {
        private val semaphore = Semaphore(config.maxConcurrentLoads)
        private val queued = AtomicInteger()

        fun acquire(observation: OperationObservation): BlockingLoadPermit {
            if (semaphore.tryAcquire()) return BlockingLoadPermit(semaphore)

            observation.loadWaitStarted(CacheWaitReason.ConcurrencyLimit, CacheLoadRole.Leader)
            val waitStarted = observation.startTimer()
            try {
                val queueSize = queued.incrementAndGet()
                if (config.maxQueuedLoads?.let { queueSize > it } == true) {
                    queued.decrementAndGet()
                    throw CacheLoadRejectedException(
                        "Load concurrency '$name' rejected a load because its queue is full.",
                    )
                }

                try {
                    val acquired = config.queueTimeout?.let { timeout ->
                        semaphore.tryAcquire(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
                    } ?: run {
                        semaphore.acquire()
                        true
                    }
                    if (!acquired) {
                        throw CacheLoadRejectedException(
                            "Load concurrency '$name' rejected a load after its queue timeout.",
                        )
                    }
                    return BlockingLoadPermit(semaphore)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw CacheLoadRejectedException(
                        "Load concurrency '$name' was interrupted while waiting.",
                    )
                } finally {
                    queued.decrementAndGet()
                }
            } finally {
                observation.loadWait(
                    CacheWaitReason.ConcurrencyLimit,
                    CacheLoadRole.Leader,
                    waitStarted,
                )
            }
        }
    }

    private class BlockingLoadPermit(
        private val semaphore: Semaphore,
    ) {
        fun release() = semaphore.release()
    }
}
