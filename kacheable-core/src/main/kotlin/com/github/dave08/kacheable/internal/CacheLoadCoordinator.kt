package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.store.DistributedSingleFlightStore
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class CacheLoadCoordinator(
    private val defaultResilience: CacheResilienceConfig,
) {
    private val inFlightMutex = Mutex()
    private val inFlightLoads = mutableMapOf<String, CompletableDeferred<Any?>>()
    private val limiterMutex = Mutex()
    private val limiters = mutableMapOf<Pair<String, Int>, Semaphore>()

    fun resilienceFor(config: CacheConfig?): CacheResilienceConfig =
        config?.resilience ?: defaultResilience

    suspend fun <R> load(
        cacheName: String,
        entryKey: String,
        store: KacheableStore,
        config: CacheConfig?,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
    ): R {
        val resilience = resilienceFor(config)
        val guardedLoad = suspend {
            runWithLoadTimeout(resilience) {
                runWithLoadLimit(cacheName, resilience.maxConcurrentLoads, loadAndSave)
            }
        }

        return when (resilience.singleFlight) {
            SingleFlightMode.None -> guardedLoad()
            SingleFlightMode.Local -> runLocalSingleFlight(
                key = "$cacheName:$entryKey",
                load = guardedLoad,
            )

            SingleFlightMode.Redis -> runDistributedSingleFlight(
                store = store,
                key = "$cacheName:$entryKey",
                resilience = resilience,
                readCached = readCached,
                loadAndSave = guardedLoad,
            )
        }
    }

    private suspend fun <R> runWithLoadTimeout(
        resilience: CacheResilienceConfig,
        block: suspend () -> R,
    ): R {
        val timeout = resilience.loadTimeout ?: return block()
        return withTimeout(timeout) { block() }
    }

    private suspend fun <R> runWithLoadLimit(
        cacheName: String,
        maxConcurrentLoads: Int?,
        block: suspend () -> R,
    ): R {
        if (maxConcurrentLoads == null) return block()
        val semaphore = limiterMutex.withLock {
            limiters.getOrPut(cacheName to maxConcurrentLoads) { Semaphore(maxConcurrentLoads) }
        }
        return semaphore.withPermit { block() }
    }

    private suspend fun <R> runLocalSingleFlight(
        key: String,
        load: suspend () -> R,
    ): R {
        val deferred = CompletableDeferred<Any?>()
        val existing = inFlightMutex.withLock {
            inFlightLoads[key] ?: deferred.also { inFlightLoads[key] = it }
        }

        if (existing !== deferred) {
            @Suppress("UNCHECKED_CAST")
            return existing.await() as R
        }

        try {
            val result = load()
            deferred.complete(result)
            return result
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            inFlightMutex.withLock {
                if (inFlightLoads[key] === deferred) {
                    inFlightLoads.remove(key)
                }
            }
        }
    }

    private suspend fun <R> runDistributedSingleFlight(
        store: KacheableStore,
        key: String,
        resilience: CacheResilienceConfig,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
    ): R {
        val distributedStore = store as? DistributedSingleFlightStore
            ?: throw IllegalStateException(
                "Cache '$key' is configured with Redis single-flight, but ${store::class.java.name} " +
                    "does not implement DistributedSingleFlightStore.",
            )

        return distributedStore.runWithDistributedSingleFlight(
            key = key,
            lockLease = resilience.loadTimeout ?: DefaultDistributedLockLease,
            waitTimeout = resilience.loadTimeout ?: DefaultDistributedWaitTimeout,
            pollInterval = DefaultDistributedPollInterval,
            readCached = readCached,
            loadAndSave = loadAndSave,
        )
    }

    companion object {
        private val DefaultDistributedLockLease = 30.seconds
        private val DefaultDistributedWaitTimeout = 30.seconds
        private val DefaultDistributedPollInterval = 50.milliseconds
    }
}

class CacheLoadTimeoutException(message: String) : RuntimeException(message)
