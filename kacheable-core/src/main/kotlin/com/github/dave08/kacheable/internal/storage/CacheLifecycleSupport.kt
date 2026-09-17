package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheExecution
import com.github.dave08.kacheable.CacheLoadResult
import com.github.dave08.kacheable.CacheLoadTrigger
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheOperationResult
import com.github.dave08.kacheable.CacheReadAttempt
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.LoadConcurrencyGroup
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.CacheLoadTimeoutException
import com.github.dave08.kacheable.internal.ObservationContext
import com.github.dave08.kacheable.internal.OperationObservation
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

/** Shared miss, refresh, coordination, and telemetry lifecycle for one scalar cache entry. */
internal suspend fun <R> invokeCacheLifecycle(
    cacheName: String,
    entryKey: String,
    store: KacheableStore,
    config: CacheConfig?,
    loadCoordinator: CacheLoadCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    backgroundScope: () -> CoroutineScope,
    missPolicy: CacheMissPolicy<R>,
    refreshPolicy: CacheRefreshPolicy<R>,
    observation: OperationObservation,
    restoreCached: (suspend () -> Unit)?,
    readCached: suspend (CacheReadAttempt) -> CachedValue<R>?,
    save: suspend (R) -> Unit,
    load: suspend (previous: R?) -> R,
): R {
    suspend fun returnCachedOrRefresh(cachedRead: CachedValue<R>): R {
        var latestCached = cachedRead.value
        val observed = applyRefreshPolicy(
            cached = cachedRead.value,
            cacheName = cacheName,
            entryKey = entryKey,
            store = store,
            config = config,
            loadCoordinator = loadCoordinator,
            loadConcurrency = loadConcurrency,
            backgroundScope = backgroundScope,
            refreshPolicy = refreshPolicy,
            observation = observation,
            loadAndSave = { trigger, execution ->
                loadAndSaveObserved(observation, trigger, execution, { load(latestCached) }, save)
            },
            readFreshCached = {
                readCached(CacheReadAttempt.SingleFlightRecheck)
                    ?.also { latestCached = it.value }
                    ?.takeUnless { refreshPolicy is CacheRefreshPolicy.RefreshIf && refreshPolicy.isStale(it.value) }
            },
            latestCached = { latestCached },
        )
        observation.complete(observed.result)
        return observed.value
    }

    readCached(CacheReadAttempt.Hot)?.let { return returnCachedOrRefresh(it) }
    restoreCached?.let { restore ->
        restore()
        readCached(CacheReadAttempt.AfterSnapshot)?.let { return returnCachedOrRefresh(it) }
    }

    val loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R = { trigger, execution ->
        loadAndSaveObserved(observation, trigger, execution, { load(null) }, save)
    }
    val observed = applyMissPolicy(
        cacheName = cacheName,
        entryKey = entryKey,
        store = store,
        config = config,
        loadCoordinator = loadCoordinator,
        loadConcurrency = loadConcurrency,
        backgroundScope = backgroundScope,
        observation = observation,
        missPolicy = missPolicy,
        readCached = { readCached(CacheReadAttempt.SingleFlightRecheck) },
        loadAndSave = loadAndSave,
    )
    observation.complete(observed.result)
    return observed.value
}

private suspend fun <R> applyMissPolicy(
    cacheName: String,
    entryKey: String,
    store: KacheableStore,
    config: CacheConfig?,
    loadCoordinator: CacheLoadCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    backgroundScope: () -> CoroutineScope,
    observation: OperationObservation,
    missPolicy: CacheMissPolicy<R>,
    readCached: suspend () -> CachedValue<R>?,
    loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R,
): ObservedValue<R> = when (missPolicy) {
    is CacheMissPolicy.Load -> loadWithPolicy(
        cacheName = cacheName,
        entryKey = entryKey,
        store = store,
        config = config,
        loadCoordinator = loadCoordinator,
        loadConcurrency = loadConcurrency,
        observation = observation,
        trigger = CacheLoadTrigger.Miss,
        execution = CacheExecution.Foreground,
        readCached = readCached,
        loadAndSave = loadAndSave,
        onFailure = { error ->
            val fallback = missPolicy.fallbackOnFailure
            if (fallback != null) {
                ObservedValue(fallback(error), CacheOperationResult.FailureFallback)
            } else {
                throw error
            }
        },
    )

    is CacheMissPolicy.LoadInBackground -> {
        val fallback = missPolicy.fallback()
        backgroundScope().launch(ObservationContext(observation)) {
            runBackgroundLoad {
                loadWithPolicy(
                    cacheName = cacheName,
                    entryKey = entryKey,
                    store = store,
                    config = config,
                    loadCoordinator = loadCoordinator,
                    loadConcurrency = loadConcurrency,
                    observation = observation,
                    trigger = CacheLoadTrigger.Miss,
                    execution = CacheExecution.Background,
                    readCached = readCached,
                    loadAndSave = loadAndSave,
                    onFailure = { throw it },
                )
            }
        }
        ObservedValue(fallback, CacheOperationResult.BackgroundFallback)
    }
}

private suspend fun <R> applyRefreshPolicy(
    cached: R,
    cacheName: String,
    entryKey: String,
    store: KacheableStore,
    config: CacheConfig?,
    loadCoordinator: CacheLoadCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    backgroundScope: () -> CoroutineScope,
    refreshPolicy: CacheRefreshPolicy<R>,
    observation: OperationObservation,
    loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R,
    readFreshCached: suspend () -> CachedValue<R>?,
    latestCached: () -> R,
): ObservedValue<R> = when (refreshPolicy) {
    is CacheRefreshPolicy.NeverRefresh -> ObservedValue(cached, CacheOperationResult.CachedValue)
    is CacheRefreshPolicy.RefreshIf -> {
        if (!refreshPolicy.isStale(cached)) {
            ObservedValue(cached, CacheOperationResult.CachedValue)
        } else if (refreshPolicy.inBackground) {
            backgroundScope().launch(ObservationContext(observation)) {
                runBackgroundLoad {
                    loadWithPolicy(
                        cacheName = cacheName,
                        entryKey = entryKey,
                        store = store,
                        config = config,
                        loadCoordinator = loadCoordinator,
                        loadConcurrency = loadConcurrency,
                        observation = observation,
                        trigger = CacheLoadTrigger.Refresh,
                        execution = CacheExecution.Background,
                        readCached = readFreshCached,
                        loadAndSave = loadAndSave,
                        onFailure = { throw it },
                    )
                }
            }
            ObservedValue(cached, CacheOperationResult.Stale)
        } else {
            loadWithPolicy(
                cacheName = cacheName,
                entryKey = entryKey,
                store = store,
                config = config,
                loadCoordinator = loadCoordinator,
                loadConcurrency = loadConcurrency,
                observation = observation,
                trigger = CacheLoadTrigger.Refresh,
                execution = CacheExecution.Foreground,
                readCached = readFreshCached,
                loadAndSave = loadAndSave,
                onFailure = { ObservedValue(latestCached(), CacheOperationResult.Stale) },
            )
        }
    }
}

private suspend fun <R> loadWithPolicy(
    cacheName: String,
    entryKey: String,
    store: KacheableStore,
    config: CacheConfig?,
    loadCoordinator: CacheLoadCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    observation: OperationObservation,
    trigger: CacheLoadTrigger,
    execution: CacheExecution,
    readCached: suspend () -> CachedValue<R>?,
    loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R,
    onFailure: suspend (Throwable) -> ObservedValue<R>,
): ObservedValue<R> {
    val resilience = loadCoordinator.resilienceFor(config)
    return try {
        val loaded = loadCoordinator.load(
            cacheName = cacheName,
            entryKey = entryKey,
            store = store,
            config = config,
            observation = observation,
            loadConcurrencyGroup = loadConcurrency,
            execution = execution,
            readCached = readCached,
            loadAndSave = { effectiveExecution -> CachedValue(loadAndSave(trigger, effectiveExecution)) },
        )
        ObservedValue(
            loaded.value,
            if (trigger == CacheLoadTrigger.Refresh) CacheOperationResult.Refreshed else CacheOperationResult.Loaded,
        )
    } catch (t: TimeoutCancellationException) {
        readCached().takeIf { resilience.staleOnTimeout }
            ?.let { ObservedValue(it.value, CacheOperationResult.Stale) }
            ?: onFailure(t)
    } catch (t: CacheLoadTimeoutException) {
        readCached().takeIf { resilience.staleOnTimeout }
            ?.let { ObservedValue(it.value, CacheOperationResult.Stale) }
            ?: onFailure(t)
    } catch (t: CancellationException) {
        throw t
    } catch (t: Throwable) {
        readCached().takeIf { resilience.staleOnFailure }
            ?.let { ObservedValue(it.value, CacheOperationResult.Stale) }
            ?: onFailure(t)
    }
}

private suspend fun <R> loadAndSaveObserved(
    observation: OperationObservation,
    trigger: CacheLoadTrigger,
    execution: CacheExecution,
    load: suspend () -> R,
    save: suspend (R) -> Unit,
): R {
    observation.loaderStarted(trigger, execution)
    val started = observation.startTimer()
    val result = try {
        load().also {
            observation.loaderCompleted(trigger, execution, CacheLoadResult.Success, started)
        }
    } catch (t: Throwable) {
        val loadResult = when (t) {
            is TimeoutCancellationException,
            is CacheLoadTimeoutException,
            -> CacheLoadResult.Timeout

            is CancellationException -> CacheLoadResult.Cancelled
            else -> CacheLoadResult.Failure
        }
        observation.loaderCompleted(trigger, execution, loadResult, started)
        throw t
    }
    save(result)
    return result
}

private suspend inline fun runBackgroundLoad(block: suspend () -> Unit) {
    try {
        block()
    } catch (_: TimeoutCancellationException) {
        // Background timeouts are recorded by telemetry and are not caller-visible.
    } catch (t: CancellationException) {
        throw t
    } catch (_: Throwable) {
        // Background failures are recorded by telemetry and intentionally not surfaced.
    }
}

private data class ObservedValue<R>(
    val value: R,
    val result: CacheOperationResult,
)
