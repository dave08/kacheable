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
import com.github.dave08.kacheable.UnresolvedCacheKeyException
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.store.AdmissionAwareDistributedSingleFlightStore
import com.github.dave08.kacheable.store.DistributedSingleFlightStore
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    private val partitionMutex = Mutex()
    private val partitionLocks = mutableMapOf<String, PartitionLock>()
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
        coordinationKey: String? = null,
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
                if (coordinationKey == null) loadAndSave(effectiveExecution)
                else withPartitionLock(coordinationKey, observation) {
                    readCached() ?: loadAndSave(effectiveExecution)
                }
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
                        runLeasedSingleFlight(
                            store = admissionAwareStore,
                            key = "$cacheName:${coordinationKey ?: entryKey}",
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
                        key = "$cacheName:${coordinationKey ?: entryKey}",
                        resilience = resilience,
                        observation = observation,
                        readCached = readCached,
                        loadAndSave = guardedLoad,
                    )
                }
            }
        }
    }

    /**
     * Claims entries together, but completes each shared scalar flight independently.
     * Ordinary batches use callbacks to retain progress and apply policies to each failed entry;
     * guarded callers omit failure handling so a failed attempt still aborts their operation.
     */
    suspend fun <R> loadMany(
        cacheName: String,
        entryKeys: List<String>,
        store: KacheableStore,
        config: CacheConfig?,
        observation: OperationObservation,
        loadConcurrencyGroup: LoadConcurrencyGroup?,
        execution: CacheExecution,
        readCached: suspend (List<String>) -> Map<String, R>,
        loadAndSave: suspend (List<String>, CacheExecution) -> Map<String, R>,
        coordinationKey: String? = null,
        onEntryResolved: (String, R) -> Unit = { _, _ -> },
        onEntryFailure: ((String, Throwable) -> Unit)? = null,
        initialMiss: Boolean = false,
        loadAdmission: (suspend (suspend () -> Map<String, R>) -> Map<String, R>)? = null,
    ): Map<String, R> {
        val keys = entryKeys.distinct()
        if (keys.isEmpty()) return emptyMap()
        val resilience = resilienceFor(config)
        val inherited = currentCoroutineContext()[CacheExecutionContext]?.execution
        val effective = if (execution == CacheExecution.Background || inherited == CacheExecution.Background)
            CacheExecution.Background else CacheExecution.Foreground
        val limiter = resolveLoadConcurrency(cacheName, resilience, loadConcurrencyGroup)
            ?.let { (name, configuration) -> limiterFor(name, configuration) }
        suspend fun <T> execute(block: suspend () -> T): T = withContext(CacheExecutionContext(effective)) {
            if (coordinationKey == null) block()
            else withPartitionLock(coordinationKey, observation, block)
        }
        suspend fun <T> admitted(block: suspend () -> T): T = runWithLoadTimeout(resilience) {
            withLoadPermit(limiter, effective, observation) { execute(block) }
        }
        suspend fun loadOwned(
            owned: List<String>,
            resolved: (String, R) -> Unit = onEntryResolved,
        ): Map<String, R> {
            val readAndLoad: suspend () -> Map<String, R> = {
                val result = readCached(owned).toMutableMap()
                result.forEach(resolved)
                val missing = owned.filterNot(result::containsKey)
                if (missing.isNotEmpty()) {
                    val loaded = loadAndSave(missing, effective)
                    result.putAll(loaded)
                    loaded.forEach(resolved)
                }
                result
            }
            // Blocking callers acquire their shared admission here, before the final read.
            // Never hold that permit while joining another owner's result.
            return loadAdmission?.invoke(readAndLoad) ?: readAndLoad()
        }
        if (resilience.singleFlight == SingleFlightMode.None) return admitted { loadOwned(keys) }
        if (resilience.singleFlight == SingleFlightMode.Redis && coordinationKey != null) {
            val readComplete = suspend { readCached(keys).takeIf { values -> keys.all(values::containsKey) } }
            if (store is AdmissionAwareDistributedSingleFlightStore && (limiter != null || initialMiss)) {
                return runWithLoadTimeout(resilience) {
                    runLeasedSingleFlight(
                        store, "$cacheName:$coordinationKey", limiter, effective, resilience, observation,
                        readCached = readComplete,
                        loadAndSave = { execute { loadOwned(keys) } },
                        initialMiss = initialMiss,
                        loadRechecks = true,
                    )
                }
            }
            return runDistributedSingleFlight(
                store, "$cacheName:$coordinationKey", resilience, observation,
                readCached = readComplete,
                loadAndSave = { admitted { loadOwned(keys) } },
            )
        }
        if (resilience.singleFlight == SingleFlightMode.Redis) {
            val distributed = store as? AdmissionAwareDistributedSingleFlightStore
                ?: throw UnsupportedOperationException("Selected-entry Redis single-flight requires lease acquisition support.")
            return runWithLoadTimeout(resilience) {
                val results = linkedMapOf<String, R>()
                val pending = keys.toMutableSet()
                val deadline = TimeSource.Monotonic.markNow() + (resilience.loadTimeout ?: DefaultDistributedWaitTimeout)
                var recheck = !initialMiss
                while (pending.isNotEmpty()) {
                    if (recheck) {
                        val cached = readCached(pending.toList())
                        results.putAll(cached)
                        cached.forEach(onEntryResolved)
                        pending.removeAll(results.keys)
                        if (pending.isEmpty()) break
                    }
                    recheck = true
                    admitted {
                        val leases = linkedMapOf<String, com.github.dave08.kacheable.store.DistributedLoadLease>()
                        try {
                            for (key in pending.sorted()) {
                                distributed.tryAcquireDistributedLoadLease(
                                    "$cacheName:$key", resilience.loadTimeout ?: DefaultDistributedLockLease,
                                )?.let { leases[key] = it }
                            }
                            if (leases.isNotEmpty()) {
                                results.putAll(loadOwned(keys.filter(leases::containsKey)))
                                // An omitted result is completed as unresolved, not retried forever.
                                pending.removeAll(leases.keys)
                            }
                        } finally {
                            withContext(NonCancellable) {
                                leases.values.forEach { it.release() }
                            }
                        }
                    }
                    if (pending.isNotEmpty()) {
                        if (deadline.hasPassedNow()) throw CacheLoadTimeoutException("Timed out waiting for selected cache entries.")
                        val started = observation.startTimer()
                        observation.loadWaitStarted(CacheWaitReason.RedisSingleFlight, CacheLoadRole.Joiner)
                        try { delay(DefaultDistributedPollInterval) }
                        finally { observation.loadWait(CacheWaitReason.RedisSingleFlight, CacheLoadRole.Joiner, started) }
                    }
                }
                results
            }
        }

        return runWithLoadTimeout(resilience) {
            val owned = linkedMapOf<String, CompletableDeferred<Any?>>()
            val joined = linkedMapOf<String, CompletableDeferred<Any?>>()
            val result = linkedMapOf<String, R>()
            try {
                // Queued background work must not claim entries before it has loader capacity.
                val permit = limiter?.acquire(effective, observation)
                try {
                    if (limiter != null && (!initialMiss || permit?.wasQueued == true)) {
                        val cached = readCached(keys)
                        result.putAll(cached)
                        cached.forEach(onEntryResolved)
                    }
                    inFlightMutex.withLock {
                        keys.filterNot(result::containsKey).forEach { key ->
                            val identity = "$cacheName:$key"
                            val existing = inFlightLoads[identity]
                            if (existing != null) joined[key] = existing
                            else CompletableDeferred<Any?>().also {
                                owned[key] = it
                                inFlightLoads[identity] = it
                            }
                        }
                    }
                    if (owned.isNotEmpty()) {
                        var ownedFailure: Throwable? = null
                        try {
                            execute {
                                loadOwned(owned.keys.toList()) { key, value ->
                                    result[key] = value
                                    onEntryResolved(key, value)
                                }
                            }
                        } catch (timeout: TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive()
                            if (onEntryFailure == null) throw timeout
                            ownedFailure = timeout
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            if (onEntryFailure == null) throw failure
                            ownedFailure = failure
                        }
                        owned.forEach { (key, deferred) ->
                            if (result.containsKey(key)) deferred.complete(result.getValue(key))
                            else {
                                val failure = ownedFailure ?: UnresolvedCacheKeyException(key)
                                deferred.completeExceptionally(failure)
                                if (ownedFailure != null) onEntryFailure?.invoke(key, failure)
                            }
                        }
                    }
                } finally { permit?.release() }

                // Publish our entries and release capacity before waiting for another owner.
                for ((key, deferred) in joined) {
                    val started = observation.startTimer()
                    observation.loadWaitStarted(CacheWaitReason.LocalSingleFlight, CacheLoadRole.Joiner)
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val value = deferred.await() as R
                        result[key] = value
                        onEntryResolved(key, value)
                    } catch (_: UnresolvedCacheKeyException) {
                        // The other loader also returned a partial map.
                    } catch (timeout: TimeoutCancellationException) {
                        // An owner's timeout is per-entry; our own deadline still aborts the wait.
                        currentCoroutineContext().ensureActive()
                        if (onEntryFailure == null) throw timeout
                        onEntryFailure(key, timeout)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        if (onEntryFailure == null) throw failure
                        onEntryFailure(key, failure)
                    } finally {
                        observation.loadWait(CacheWaitReason.LocalSingleFlight, CacheLoadRole.Joiner, started)
                    }
                }
                result
            } catch (failure: Throwable) {
                owned.values.forEach { it.completeExceptionally(failure) }
                throw failure
            } finally {
                withContext(NonCancellable) {
                    inFlightMutex.withLock {
                        owned.forEach { (key, deferred) ->
                            val identity = "$cacheName:$key"
                            if (inFlightLoads[identity] === deferred) inFlightLoads.remove(identity)
                        }
                    }
                }
            }
        }
    }

    private class PartitionLock(val mutex: Mutex = Mutex(), var users: Int = 0)

    private suspend fun <R> withPartitionLock(key: String, observation: OperationObservation, block: suspend () -> R): R {
        val lock = partitionMutex.withLock {
            partitionLocks.getOrPut(key) { PartitionLock() }.also { it.users++ }
        }
        try {
            acquirePartitionLock(lock.mutex, observation)
            try {
                return block()
            } finally {
                lock.mutex.unlock()
            }
        } finally {
            withContext(NonCancellable) {
                partitionMutex.withLock {
                    lock.users--
                    if (lock.users == 0) partitionLocks.remove(key)
                }
            }
        }
    }

    private suspend fun acquirePartitionLock(mutex: Mutex, observation: OperationObservation) {
        if (mutex.tryLock()) return
        val started = observation.startTimer()
        observation.loadWaitStarted(CacheWaitReason.PartitionCoordination, CacheLoadRole.Joiner)
        try {
            mutex.lock()
        } finally {
            observation.loadWait(CacheWaitReason.PartitionCoordination, CacheLoadRole.Joiner, started)
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

    /**
     * Polling checks belong before a new ownership attempt. Once ownership is acquired, a loader
     * that rechecks under its partition mutex owns the final read, so its snapshot can be reused.
     * [initialMiss] skips only the first polling read; it never skips the owned load's recheck.
     */
    private suspend fun <R> runLeasedSingleFlight(
        store: AdmissionAwareDistributedSingleFlightStore,
        key: String,
        limiter: LoadLimiter?,
        execution: CacheExecution,
        resilience: CacheResilienceConfig,
        observation: OperationObservation,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
        initialMiss: Boolean = false,
        loadRechecks: Boolean = false,
    ): R {
        val lockLease = resilience.loadTimeout ?: DefaultDistributedLockLease
        val waitTimeout = resilience.loadTimeout ?: DefaultDistributedWaitTimeout
        val deadline = TimeSource.Monotonic.markNow() + waitTimeout
        var redisWaitStarted = false
        var redisWaitDurationNanos = 0L
        var recheck = !initialMiss

        try {
            while (deadline.hasNotPassedNow()) {
                if (recheck) readCached()?.let { return it }
                recheck = true

                val permit = limiter?.acquire(execution, observation)
                try {
                    if (permit != null && (!initialMiss || permit.wasQueued)) readCached()?.let { return it }
                    val lease = store.tryAcquireDistributedLoadLease(key, lockLease)
                    if (lease != null) {
                        try {
                            return if (loadRechecks) loadAndSave() else readCached() ?: loadAndSave()
                        } finally {
                            withContext(NonCancellable) {
                                lease.release()
                            }
                        }
                    }
                } finally {
                    permit?.release()
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
                return LoadPermit(this, execution, wasQueued = true)
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
        val wasQueued: Boolean = false,
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
