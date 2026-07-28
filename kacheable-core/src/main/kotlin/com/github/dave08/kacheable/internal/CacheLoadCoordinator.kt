package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.CacheLoadRole
import com.github.dave08.kacheable.CacheLoadRejectedException
import com.github.dave08.kacheable.CacheExecution
import com.github.dave08.kacheable.CacheWaitReason
import com.github.dave08.kacheable.LoadConcurrencyConfig
import com.github.dave08.kacheable.LoadConcurrencyGroup
import com.github.dave08.kacheable.LoadConcurrencySettings
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.store.AdmissionAwareDistributedSingleFlightStore
import com.github.dave08.kacheable.store.DistributedSingleFlightStore
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class CacheLoadCoordinator(
    private val defaultResilience: CacheResilienceConfig,
    private val loadConcurrency: LoadConcurrencySettings,
) {
    private val inFlightMutex = Mutex()
    private val inFlightLoads = mutableMapOf<String, CompletableDeferred<Any?>>()
    private val limiterMutex = Mutex()
    private val limiters = mutableMapOf<String, LoadLimiter>()
    private val declaredGroupDefaults = mutableMapOf<String, LoadConcurrencyConfig>()

    fun resilienceFor(config: CacheConfig?): CacheResilienceConfig =
        config?.resilience ?: defaultResilience

    suspend fun <R> load(
        cacheName: String,
        entryKey: String,
        store: KacheableStore,
        config: CacheConfig?,
        observation: OperationObservation,
        loadConcurrencyGroup: LoadConcurrencyGroup?,
        execution: CacheExecution,
        readCached: suspend () -> R?,
        loadAndSave: suspend (CacheExecution) -> R,
    ): R {
        val resilience = resilienceFor(config)
        val concurrency = resolveLoadConcurrency(cacheName, resilience, loadConcurrencyGroup)
        val inheritedExecution = currentCoroutineContext()[CacheExecutionContext]?.execution
        val effectiveExecution = if (
            execution == CacheExecution.Background ||
            inheritedExecution == CacheExecution.Background
        ) {
            CacheExecution.Background
        } else {
            CacheExecution.Foreground
        }
        val executeLoad = suspend {
            withContext(CacheExecutionContext(effectiveExecution)) {
                loadAndSave(effectiveExecution)
            }
        }
        val limiter = concurrency?.let { (name, resolvedConfig) ->
            limiterFor(name, resolvedConfig)
        }
        val guardedLoad = suspend {
            runWithLoadTimeout(resilience) {
                withLoadPermit(limiter, effectiveExecution, observation, executeLoad)
            }
        }

        return when (resilience.singleFlight) {
            SingleFlightMode.None -> guardedLoad()
            SingleFlightMode.Local -> if (limiter == null) {
                runLocalSingleFlight(
                    key = "$cacheName:$entryKey",
                    observation = observation,
                    load = guardedLoad,
                )
            } else {
                runWithLoadTimeout(resilience) {
                    runAdmittedLocalSingleFlight(
                        key = "$cacheName:$entryKey",
                        limiter = limiter,
                        execution = effectiveExecution,
                        observation = observation,
                        readCached = readCached,
                        load = executeLoad,
                    )
                }
            }

            SingleFlightMode.Redis -> {
                val admissionAwareStore = store as? AdmissionAwareDistributedSingleFlightStore
                if (limiter != null && admissionAwareStore != null) {
                    runWithLoadTimeout(resilience) {
                        runAdmittedDistributedSingleFlight(
                            store = admissionAwareStore,
                            key = "$cacheName:$entryKey",
                            limiter = limiter,
                            execution = effectiveExecution,
                            resilience = resilience,
                            observation = observation,
                            readCached = readCached,
                            loadAndSave = executeLoad,
                        )
                    }
                } else {
                    runDistributedSingleFlight(
                        store = store,
                        key = "$cacheName:$entryKey",
                        resilience = resilience,
                        observation = observation,
                        readCached = readCached,
                        loadAndSave = guardedLoad,
                    )
                }
            }
        }
    }

    private suspend fun <R> runWithLoadTimeout(
        resilience: CacheResilienceConfig,
        block: suspend () -> R,
    ): R {
        val timeout = resilience.loadTimeout ?: return block()
        return withTimeout(timeout) { block() }
    }

    private suspend fun <R> withLoadPermit(
        limiter: LoadLimiter?,
        execution: CacheExecution,
        observation: OperationObservation,
        block: suspend () -> R,
    ): R {
        if (limiter == null) return block()
        val permit = limiter.acquire(execution, observation)
        try {
            return block()
        } finally {
            permit.release()
        }
    }

    private suspend fun limiterFor(
        name: String,
        config: LoadConcurrencyConfig,
    ): LoadLimiter = limiterMutex.withLock {
        limiters.getOrPut(name) { LoadLimiter(name, config) }
            .also { existing ->
                require(existing.config == config) {
                    "Load concurrency '$name' was resolved with conflicting configurations."
                }
            }
    }

    private suspend fun resolveLoadConcurrency(
        cacheName: String,
        resilience: CacheResilienceConfig,
        group: LoadConcurrencyGroup?,
    ): Pair<String, LoadConcurrencyConfig>? {
        if (group != null) {
            require(resilience.maxConcurrentLoads == null) {
                "Cache '$cacheName' declares load concurrency group '${group.name}' and also " +
                    "configures the legacy per-cache maxConcurrentLoads limit."
            }
            limiterMutex.withLock {
                val previous = declaredGroupDefaults.putIfAbsent(group.name, group.defaults)
                require(previous == null || previous == group.defaults) {
                    "Load concurrency group '${group.name}' was declared with conflicting defaults."
                }
            }
            return "group:${group.name}" to
                (loadConcurrency.overrides[group] ?: group.defaults)
        }

        val perCache = resilience.maxConcurrentLoads?.let(::LoadConcurrencyConfig)
            ?: loadConcurrency.default
            ?: return null
        return "cache:$cacheName" to perCache
    }

    private suspend fun <R> runLocalSingleFlight(
        key: String,
        observation: OperationObservation,
        load: suspend () -> R,
    ): R {
        val deferred = CompletableDeferred<Any?>()
        val existing = inFlightMutex.withLock {
            inFlightLoads[key] ?: deferred.also { inFlightLoads[key] = it }
        }

        if (existing !== deferred) {
            observation.loadWaitStarted(CacheWaitReason.LocalSingleFlight, CacheLoadRole.Joiner)
            val started = observation.startTimer()
            @Suppress("UNCHECKED_CAST")
            return try {
                existing.await() as R
            } finally {
                observation.loadWait(CacheWaitReason.LocalSingleFlight, CacheLoadRole.Joiner, started)
            }
        }

        try {
            val result = load()
            deferred.complete(result)
            return result
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            withContext(NonCancellable) {
                inFlightMutex.withLock {
                    if (inFlightLoads[key] === deferred) {
                        inFlightLoads.remove(key)
                    }
                }
            }
        }
    }

    private suspend fun <R> runAdmittedLocalSingleFlight(
        key: String,
        limiter: LoadLimiter,
        execution: CacheExecution,
        observation: OperationObservation,
        readCached: suspend () -> R?,
        load: suspend () -> R,
    ): R {
        val permit = limiter.acquire(execution, observation)
        var permitHeld = true
        var leader: CompletableDeferred<Any?>? = null
        try {
            readCached()?.let { return it }

            val deferred = CompletableDeferred<Any?>()
            val existing = inFlightMutex.withLock {
                inFlightLoads[key] ?: deferred.also { inFlightLoads[key] = it }
            }
            if (existing !== deferred) {
                permit.release()
                permitHeld = false
                observation.loadWaitStarted(CacheWaitReason.LocalSingleFlight, CacheLoadRole.Joiner)
                val started = observation.startTimer()
                @Suppress("UNCHECKED_CAST")
                return try {
                    existing.await() as R
                } finally {
                    observation.loadWait(CacheWaitReason.LocalSingleFlight, CacheLoadRole.Joiner, started)
                }
            }

            leader = deferred
            try {
                val result = load()
                deferred.complete(result)
                return result
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
                throw t
            }
        } finally {
            if (permitHeld) permit.release()
            leader?.let { deferred ->
                withContext(NonCancellable) {
                    inFlightMutex.withLock {
                        if (inFlightLoads[key] === deferred) {
                            inFlightLoads.remove(key)
                        }
                    }
                }
            }
        }
    }

    private suspend fun <R> runAdmittedDistributedSingleFlight(
        store: AdmissionAwareDistributedSingleFlightStore,
        key: String,
        limiter: LoadLimiter,
        execution: CacheExecution,
        resilience: CacheResilienceConfig,
        observation: OperationObservation,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
    ): R {
        val lockLease = resilience.loadTimeout ?: DefaultDistributedLockLease
        val waitTimeout = resilience.loadTimeout ?: DefaultDistributedWaitTimeout
        val deadline = TimeSource.Monotonic.markNow() + waitTimeout
        var redisWaitStarted = false
        var redisWaitDurationNanos = 0L

        try {
            while (deadline.hasNotPassedNow()) {
                readCached()?.let { return it }

                val permit = limiter.acquire(execution, observation)
                try {
                    readCached()?.let { return it }
                    val lease = store.tryAcquireDistributedLoadLease(key, lockLease)
                    if (lease != null) {
                        try {
                            return readCached() ?: loadAndSave()
                        } finally {
                            withContext(NonCancellable) {
                                lease.release()
                            }
                        }
                    }
                } finally {
                    permit.release()
                }

                if (observation.isEnabled && !redisWaitStarted) {
                    observation.loadWaitStarted(CacheWaitReason.RedisSingleFlight, CacheLoadRole.Joiner)
                    redisWaitStarted = true
                }
                if (!observation.isEnabled) {
                    delay(DefaultDistributedPollInterval)
                } else {
                    val pollStarted = observation.startTimer()
                    try {
                        delay(DefaultDistributedPollInterval)
                    } finally {
                        redisWaitDurationNanos += elapsedSince(pollStarted)
                    }
                }
            }

            readCached()?.let { return it }
            throw CacheLoadTimeoutException("Timed out waiting for distributed single-flight lock for '$key'.")
        } finally {
            if (redisWaitStarted) {
                observation.loadWaitDuration(
                    CacheWaitReason.RedisSingleFlight,
                    CacheLoadRole.Joiner,
                    redisWaitDurationNanos,
                )
            }
        }
    }

    private suspend fun <R> runDistributedSingleFlight(
        store: KacheableStore,
        key: String,
        resilience: CacheResilienceConfig,
        observation: OperationObservation,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
    ): R {
        val distributedStore = store as? DistributedSingleFlightStore
            ?: throw IllegalStateException(
                "Cache '$key' is configured with Redis single-flight, but ${store::class.java.name} " +
                    "does not implement DistributedSingleFlightStore.",
            )

        if (!observation.isEnabled) {
            return distributedStore.runWithDistributedSingleFlight(
                key = key,
                lockLease = resilience.loadTimeout ?: DefaultDistributedLockLease,
                waitTimeout = resilience.loadTimeout ?: DefaultDistributedWaitTimeout,
                pollInterval = DefaultDistributedPollInterval,
                readCached = readCached,
                loadAndSave = loadAndSave,
            )
        }

        var loaderDurationNanos = 0L
        var loaderExecuted = false
        observation.loadWaitStarted(CacheWaitReason.RedisSingleFlight, CacheLoadRole.Joiner)
        val started = observation.startTimer()
        return try {
            distributedStore.runWithDistributedSingleFlight(
                key = key,
                lockLease = resilience.loadTimeout ?: DefaultDistributedLockLease,
                waitTimeout = resilience.loadTimeout ?: DefaultDistributedWaitTimeout,
                pollInterval = DefaultDistributedPollInterval,
                readCached = readCached,
                loadAndSave = {
                    loaderExecuted = true
                    val loaderStarted = observation.startTimer()
                    try {
                        loadAndSave()
                    } finally {
                        loaderDurationNanos = elapsedSince(loaderStarted)
                    }
                },
            )
        } finally {
            val total = elapsedSince(started)
            val waitStarted = observation.startTimer() - (total - loaderDurationNanos).coerceAtLeast(0)
            observation.loadWait(
                CacheWaitReason.RedisSingleFlight,
                if (loaderExecuted) CacheLoadRole.Leader else CacheLoadRole.Joiner,
                waitStarted,
            )
        }
    }

    companion object {
        private val DefaultDistributedLockLease = 30.seconds
        private val DefaultDistributedWaitTimeout = 30.seconds
        private val DefaultDistributedPollInterval = 50.milliseconds
    }

    private class LoadLimiter(
        private val name: String,
        val config: LoadConcurrencyConfig,
    ) {
        private val mutex = Mutex()
        private val queue = ArrayDeque<QueuedLoad>()
        private var activeLoads = 0
        private var activeBackgroundLoads = 0
        private var foregroundGrantsWhileBackgroundWaits = 0

        suspend fun acquire(
            execution: CacheExecution,
            observation: OperationObservation,
        ): LoadPermit {
            val queuedLoad = mutex.withLock {
                if (queue.isEmpty() && canGrant(execution)) {
                    recordGrant(execution, backgroundWaiting = false)
                    null
                } else {
                    if (config.maxQueuedLoads?.let { queue.size >= it } == true) {
                        throw CacheLoadRejectedException(
                            "Load concurrency '$name' rejected a load because its queue is full.",
                        )
                    }
                    QueuedLoad(execution).also {
                        queue.addLast(it)
                        drainQueue()
                    }
                }
            }
            if (queuedLoad == null) return LoadPermit(this, execution)

            observation.loadWaitStarted(CacheWaitReason.ConcurrencyLimit, CacheLoadRole.Leader)
            val waitStarted = observation.startTimer()
            try {
                val acquired = try {
                    config.queueTimeout?.let { timeout ->
                        withTimeoutOrNull(timeout) {
                            queuedLoad.ready.await()
                            true
                        } ?: false
                    } ?: run {
                        queuedLoad.ready.await()
                        true
                    }
                } catch (t: Throwable) {
                    withContext(NonCancellable) {
                        abandon(queuedLoad)
                    }
                    throw t
                }
                if (!acquired && !claimBoundaryGrantOrRemove(queuedLoad)) {
                    throw CacheLoadRejectedException(
                        "Load concurrency '$name' rejected a load after its queue timeout.",
                    )
                }
                return LoadPermit(this, execution)
            } finally {
                observation.loadWait(
                    CacheWaitReason.ConcurrencyLimit,
                    CacheLoadRole.Leader,
                    waitStarted,
                )
            }
        }

        suspend fun release(execution: CacheExecution) {
            mutex.withLock {
                releaseGrant(execution)
                drainQueue()
            }
        }

        private suspend fun abandon(load: QueuedLoad) {
            mutex.withLock {
                when (load.state) {
                    QueueState.Queued -> {
                        queue.remove(load)
                        load.state = QueueState.Cancelled
                        drainQueue()
                    }

                    QueueState.Granted -> {
                        load.state = QueueState.Cancelled
                        releaseGrant(load.execution)
                        drainQueue()
                    }

                    QueueState.Cancelled -> Unit
                }
            }
        }

        private suspend fun claimBoundaryGrantOrRemove(load: QueuedLoad): Boolean =
            mutex.withLock {
                when (load.state) {
                    QueueState.Queued -> {
                        queue.remove(load)
                        load.state = QueueState.Cancelled
                        drainQueue()
                        false
                    }

                    QueueState.Granted -> true
                    QueueState.Cancelled -> false
                }
            }

        private fun drainQueue() {
            while (activeLoads < config.maxConcurrentLoads) {
                val background = queue.firstOrNull {
                    it.execution == CacheExecution.Background && canGrant(it.execution)
                }
                val foreground = queue.firstOrNull {
                    it.execution == CacheExecution.Foreground && canGrant(it.execution)
                }
                val next = when {
                    foreground == null -> background
                    background == null -> foreground
                    foregroundGrantsWhileBackgroundWaits >= ForegroundGrantBurst -> background
                    else -> foreground
                } ?: return

                queue.remove(next)
                next.state = QueueState.Granted
                recordGrant(
                    next.execution,
                    backgroundWaiting = background != null && next.execution == CacheExecution.Foreground,
                )
                next.ready.complete(Unit)
            }
        }

        private fun canGrant(execution: CacheExecution): Boolean {
            if (activeLoads >= config.maxConcurrentLoads) return false
            if (execution != CacheExecution.Background) return true
            val backgroundLimit = config.maxConcurrentBackgroundLoads ?: config.maxConcurrentLoads
            return activeBackgroundLoads < backgroundLimit
        }

        private fun recordGrant(
            execution: CacheExecution,
            backgroundWaiting: Boolean,
        ) {
            activeLoads++
            if (execution == CacheExecution.Background) {
                activeBackgroundLoads++
                foregroundGrantsWhileBackgroundWaits = 0
            } else if (backgroundWaiting) {
                foregroundGrantsWhileBackgroundWaits++
            } else {
                foregroundGrantsWhileBackgroundWaits = 0
            }
        }

        private fun releaseGrant(execution: CacheExecution) {
            activeLoads = (activeLoads - 1).coerceAtLeast(0)
            if (execution == CacheExecution.Background) {
                activeBackgroundLoads = (activeBackgroundLoads - 1).coerceAtLeast(0)
            }
        }

        private class QueuedLoad(
            val execution: CacheExecution,
            val ready: CompletableDeferred<Unit> = CompletableDeferred(),
            var state: QueueState = QueueState.Queued,
        )

        private enum class QueueState {
            Queued,
            Granted,
            Cancelled,
        }

        private companion object {
            const val ForegroundGrantBurst = 8
        }
    }

    private class LoadPermit(
        private val limiter: LoadLimiter,
        private val execution: CacheExecution,
    ) {
        private val released = AtomicBoolean()

        suspend fun release() {
            if (released.compareAndSet(false, true)) {
                withContext(NonCancellable) {
                    limiter.release(execution)
                }
            }
        }
    }
}

private class CacheExecutionContext(
    val execution: CacheExecution,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CacheExecutionContext>
}

class CacheLoadTimeoutException(message: String) : RuntimeException(message)
