package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheExecution
import com.github.dave08.kacheable.CacheLoadTrigger
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheOperationResult
import com.github.dave08.kacheable.CacheReadAttempt
import com.github.dave08.kacheable.CacheReadResult
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheWriteResult
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.LoadConcurrencyGroup
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.CacheLoadTimeoutException
import com.github.dave08.kacheable.internal.BlockingLoadConcurrencyCoordinator
import com.github.dave08.kacheable.internal.CacheResultPolicy
import com.github.dave08.kacheable.internal.ObservationContext
import com.github.dave08.kacheable.internal.OperationObservation
import com.github.dave08.kacheable.internal.snapshot.CacheSnapshotCoordinator
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

internal suspend fun <R> KacheableStore.invokeAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    configs: Map<String, CacheConfig>,
    loadCoordinator: CacheLoadCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    snapshotCoordinator: CacheSnapshotCoordinator?,
    backgroundScope: () -> CoroutineScope,
    codec: CacheValueCodec<R>,
    missPolicy: CacheMissPolicy<R>,
    refreshPolicy: CacheRefreshPolicy<R>,
    storeResultIf: (R) -> Boolean,
    observation: OperationObservation,
    block: suspend (previous: R?) -> R,
): R = invokeObservedAtAddress(
    entryName = entryName,
    cacheName = cacheName,
    configs = configs,
    loadCoordinator = loadCoordinator,
    loadConcurrency = loadConcurrency,
    snapshotCoordinator = snapshotCoordinator,
    backgroundScope = backgroundScope,
    missPolicy = missPolicy,
    refreshPolicy = refreshPolicy,
    observation = observation,
    readDecoded = { attempt ->
        readObservedRawValue(entryName, configs[cacheName], observation, attempt)
            ?.let { DecodedRead(CacheResultPolicy.decodeCachedResult(it, configs[cacheName], codec)) }
    },
    saveResult = { result ->
        saveLoaderResult(entryName, result, configs[cacheName], storeResultIf, codec, observation)
    },
    block = block,
)

internal suspend fun <R> KacheableStore.invokeAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    configs: Map<String, CacheConfig>,
    loadCoordinator: CacheLoadCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    snapshotCoordinator: CacheSnapshotCoordinator?,
    backgroundScope: () -> CoroutineScope,
    returnView: CacheReturn<R, *>,
    missPolicy: CacheMissPolicy<R>,
    refreshPolicy: CacheRefreshPolicy<R>,
    storeResultIf: (R) -> Boolean,
    observation: OperationObservation,
    block: suspend (previous: R?) -> R,
): R = invokeObservedAtAddress(
    entryName = entryName,
    cacheName = cacheName,
    configs = configs,
    loadCoordinator = loadCoordinator,
    loadConcurrency = loadConcurrency,
    snapshotCoordinator = snapshotCoordinator,
    backgroundScope = backgroundScope,
    missPolicy = missPolicy,
    refreshPolicy = refreshPolicy,
    observation = observation,
    readDecoded = { attempt ->
        readDecodedCachedValue(entryName, configs[cacheName], returnView, observation, attempt)
    },
    saveResult = { result ->
        saveLoaderResult(entryName, result, configs[cacheName], storeResultIf, returnView.codec, observation)
    },
    block = block,
)

private suspend fun <R> KacheableStore.invokeObservedAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    configs: Map<String, CacheConfig>,
    loadCoordinator: CacheLoadCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    snapshotCoordinator: CacheSnapshotCoordinator?,
    backgroundScope: () -> CoroutineScope,
    missPolicy: CacheMissPolicy<R>,
    refreshPolicy: CacheRefreshPolicy<R>,
    observation: OperationObservation,
    readDecoded: suspend (CacheReadAttempt) -> DecodedRead<R>?,
    saveResult: suspend (R) -> Unit,
    block: suspend (previous: R?) -> R,
): R {
    val config = configs[cacheName]
    readDecoded(CacheReadAttempt.Hot)?.let { cachedRead ->
        val cached = cachedRead.value
        val observed = applyRefreshPolicy(
            cached = cached,
            cacheName = cacheName,
            entryName = entryName,
            store = this,
            config = config,
            loadCoordinator = loadCoordinator,
            loadConcurrency = loadConcurrency,
            backgroundScope = backgroundScope,
            refreshPolicy = refreshPolicy,
            observation = observation,
            loadAndSave = { trigger, execution ->
                loadAndSaveObserved(observation, trigger, execution, { block(cached) }, saveResult)
            },
            readFreshCached = {
                readDecoded(CacheReadAttempt.SingleFlightRecheck)?.value
                    ?.takeUnless { refreshPolicy is CacheRefreshPolicy.RefreshIf && refreshPolicy.isStale(it) }
            },
        )
        observation.complete(observed.result)
        return observed.value
    }

    snapshotCoordinator?.restoreEntry(cacheName, entryName)
    readDecoded(CacheReadAttempt.AfterSnapshot)?.let { cachedRead ->
        val cached = cachedRead.value
        val observed = applyRefreshPolicy(
            cached = cached,
            cacheName = cacheName,
            entryName = entryName,
            store = this,
            config = config,
            loadCoordinator = loadCoordinator,
            loadConcurrency = loadConcurrency,
            backgroundScope = backgroundScope,
            refreshPolicy = refreshPolicy,
            observation = observation,
            loadAndSave = { trigger, execution ->
                loadAndSaveObserved(observation, trigger, execution, { block(cached) }, saveResult)
            },
            readFreshCached = {
                readDecoded(CacheReadAttempt.SingleFlightRecheck)?.value
                    ?.takeUnless { refreshPolicy is CacheRefreshPolicy.RefreshIf && refreshPolicy.isStale(it) }
            },
        )
        observation.complete(observed.result)
        return observed.value
    }

    val readCached = suspend { readDecoded(CacheReadAttempt.SingleFlightRecheck)?.value }
    val loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R = { trigger, execution ->
        loadAndSaveObserved(observation, trigger, execution, { block(null) }, saveResult)
    }

    val observed = when (missPolicy) {
        is CacheMissPolicy.Load -> loadWithPolicy(
            cacheName = cacheName,
            entryName = entryName,
            store = this,
            config = config,
            loadCoordinator = loadCoordinator,
            loadConcurrency = loadConcurrency,
            observation = observation,
            trigger = CacheLoadTrigger.Miss,
            execution = CacheExecution.Foreground,
            readCached = readCached,
            loadAndSave = loadAndSave,
            onFailure = { error ->
                missPolicy.fallbackOnFailure?.invoke(error)
                    ?.let { ObservedValue(it, CacheOperationResult.FailureFallback) }
                    ?: throw error
            },
        )

        is CacheMissPolicy.LoadInBackground -> {
            val fallback = missPolicy.fallback()
            backgroundScope().launch(ObservationContext(observation)) {
                runBackgroundLoad {
                    loadWithPolicy(
                        cacheName = cacheName,
                        entryName = entryName,
                        store = this@invokeObservedAtAddress,
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
    observation.complete(observed.result)
    return observed.value
}

private suspend fun <R> KacheableStore.applyRefreshPolicy(
    cached: R,
    cacheName: String,
    entryName: StoreEntryName,
    store: KacheableStore,
    config: CacheConfig?,
    loadCoordinator: CacheLoadCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    backgroundScope: () -> CoroutineScope,
    refreshPolicy: CacheRefreshPolicy<R>,
    observation: OperationObservation,
    loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R,
    readFreshCached: suspend () -> R?,
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
                        entryName = entryName,
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
                entryName = entryName,
                store = store,
                config = config,
                loadCoordinator = loadCoordinator,
                loadConcurrency = loadConcurrency,
                observation = observation,
                trigger = CacheLoadTrigger.Refresh,
                execution = CacheExecution.Foreground,
                readCached = readFreshCached,
                loadAndSave = loadAndSave,
                onFailure = { ObservedValue(cached, CacheOperationResult.Stale) },
            )
        }
    }
}

private suspend fun <R> KacheableStore.readDecodedCachedValue(
    entryName: StoreEntryName,
    config: CacheConfig?,
    returnView: CacheReturn<R, *>,
    observation: OperationObservation,
    attempt: CacheReadAttempt,
): DecodedRead<R>? {
    val raw = readObservedRawValue(entryName, config, observation, attempt) ?: return null
    return DecodedRead(CacheResultPolicy.decodeCachedResult(raw, config, returnView.codec))
}

private suspend fun KacheableStore.readObservedRawValue(
    entryName: StoreEntryName,
    config: CacheConfig?,
    observation: OperationObservation,
    attempt: CacheReadAttempt,
): String? {
    val started = observation.startTimer()
    val result = readCachedValue(entryName, config)
    observation.storageRead(
        attempt,
        if (result == null) CacheReadResult.Absent else CacheReadResult.Present,
        started,
    )
    return result
}

private suspend fun KacheableStore.readCachedValue(
    entryName: StoreEntryName,
    config: CacheConfig?,
): String? {
    val result = if (entryName is StoreEntryName.Flat && config?.expiryType == ExpiryType.after_access) {
        getValueRefreshingExpire(entryName.key, config.expiry)
    } else {
        get(entryName)
    }
    if (result != null && entryName is StoreEntryName.Layered && config?.expiryType == ExpiryType.after_access) {
        setExpire(entryName.key, config.expiry)
    }
    return result
}

private suspend fun <R> KacheableStore.saveLoaderResult(
    entryName: StoreEntryName,
    blockResult: R,
    config: CacheConfig?,
    storeResultIf: (R) -> Boolean,
    codec: CacheValueCodec<R>,
    observation: OperationObservation,
) {
    val resultToSave = CacheResultPolicy.encodeResultToSave(blockResult, config, storeResultIf, codec)
    if (resultToSave == null) {
        observation.storageWrite(CacheWriteResult.Skipped, observation.startTimer())
    } else {
        save(entryName, resultToSave, config, observation)
    }
}

private suspend fun <R> loadWithPolicy(
    cacheName: String,
    entryName: StoreEntryName,
    store: KacheableStore,
    config: CacheConfig?,
    loadCoordinator: CacheLoadCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    observation: OperationObservation,
    trigger: CacheLoadTrigger,
    execution: CacheExecution,
    readCached: suspend () -> R?,
    loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R,
    onFailure: suspend (Throwable) -> ObservedValue<R>,
): ObservedValue<R> {
    val resilience = loadCoordinator.resilienceFor(config)
    return try {
        val loaded = loadCoordinator.load(
            cacheName = cacheName,
            entryKey = entryName.cacheLoadKey,
            store = store,
            config = config,
            observation = observation,
            loadConcurrencyGroup = loadConcurrency,
            execution = execution,
            readCached = readCached,
            loadAndSave = { effectiveExecution -> loadAndSave(trigger, effectiveExecution) },
        )
        ObservedValue(
            loaded,
            if (trigger == CacheLoadTrigger.Refresh) CacheOperationResult.Refreshed else CacheOperationResult.Loaded,
        )
    } catch (t: TimeoutCancellationException) {
        readCached().takeIf { resilience.staleOnTimeout }
            ?.let { ObservedValue(it, CacheOperationResult.Stale) }
            ?: onFailure(t)
    } catch (t: CacheLoadTimeoutException) {
        readCached().takeIf { resilience.staleOnTimeout }
            ?.let { ObservedValue(it, CacheOperationResult.Stale) }
            ?: onFailure(t)
    } catch (t: CancellationException) {
        throw t
    } catch (t: Throwable) {
        readCached().takeIf { resilience.staleOnFailure }
            ?.let { ObservedValue(it, CacheOperationResult.Stale) }
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
            observation.loaderCompleted(
                trigger,
                execution,
                com.github.dave08.kacheable.CacheLoadResult.Success,
                started,
            )
        }
    } catch (t: Throwable) {
        val loadResult = when (t) {
            is TimeoutCancellationException,
            is CacheLoadTimeoutException,
            -> com.github.dave08.kacheable.CacheLoadResult.Timeout

            is CancellationException -> com.github.dave08.kacheable.CacheLoadResult.Cancelled
            else -> com.github.dave08.kacheable.CacheLoadResult.Failure
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

internal fun <R> BlockingKacheableStore.invokeAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    configs: Map<String, CacheConfig>,
    loadCoordinator: BlockingLoadConcurrencyCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    codec: CacheValueCodec<R>,
    saveResultIf: (R) -> Boolean,
    observation: OperationObservation,
    block: () -> R,
): R = invokeObservedAtAddress(
    entryName,
    cacheName,
    configs[cacheName],
    loadCoordinator,
    loadConcurrency,
    observation,
    decode = { CacheResultPolicy.decodeCachedResult(it, configs[cacheName], codec) },
    encode = { CacheResultPolicy.encodeResultToSave(it, configs[cacheName], saveResultIf, codec) },
    block = block,
)

internal fun <R> BlockingKacheableStore.invokeAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    configs: Map<String, CacheConfig>,
    loadCoordinator: BlockingLoadConcurrencyCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    returnView: CacheReturn<R, *>,
    saveResultIf: (R) -> Boolean,
    observation: OperationObservation,
    block: () -> R,
): R {
    val config = configs[cacheName]
    return invokeObservedAtAddress(
        entryName,
        cacheName,
        config,
        loadCoordinator,
        loadConcurrency,
        observation,
        decode = { raw -> CacheResultPolicy.decodeCachedResult(raw, config, returnView.codec) },
        encode = { CacheResultPolicy.encodeResultToSave(it, config, saveResultIf, returnView.codec) },
        block = block,
    )
}

private fun <R> BlockingKacheableStore.invokeObservedAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    config: CacheConfig?,
    loadCoordinator: BlockingLoadConcurrencyCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    observation: OperationObservation,
    decode: (String) -> R?,
    encode: (R) -> String?,
    block: () -> R,
): R {
    val readStarted = observation.startTimer()
    val raw = if (entryName is StoreEntryName.Flat && config?.expiryType == ExpiryType.after_access) {
        getValueRefreshingExpire(entryName.key, config.expiry)
    } else {
        get(entryName)
    }
    observation.storageRead(
        CacheReadAttempt.Hot,
        if (raw == null) CacheReadResult.Absent else CacheReadResult.Present,
        readStarted,
    )

    raw?.let { value ->
        if (entryName is StoreEntryName.Layered && config?.expiryType == ExpiryType.after_access) {
            setExpire(entryName.key, config.expiry)
        }
        decode(value)?.let { decoded ->
            observation.complete(CacheOperationResult.CachedValue)
            return decoded
        }
    }

    val loaded = loadCoordinator.withPermit(cacheName, loadConcurrency, observation) {
        observation.loaderStarted(CacheLoadTrigger.Miss, CacheExecution.Foreground)
        val loadStarted = observation.startTimer()
        try {
            block().also {
                observation.loaderCompleted(
                    CacheLoadTrigger.Miss,
                    CacheExecution.Foreground,
                    com.github.dave08.kacheable.CacheLoadResult.Success,
                    loadStarted,
                )
            }
        } catch (t: Throwable) {
            observation.loaderCompleted(
                CacheLoadTrigger.Miss,
                CacheExecution.Foreground,
                com.github.dave08.kacheable.CacheLoadResult.Failure,
                loadStarted,
            )
            throw t
        }
    }
    val encoded = encode(loaded)
    if (encoded == null) {
        observation.storageWrite(CacheWriteResult.Skipped, observation.startTimer())
    } else {
        save(entryName, encoded, config, observation)
    }
    observation.complete(CacheOperationResult.Loaded)
    return loaded
}

private val StoreEntryName.cacheLoadKey: String
    get() = when (this) {
        is StoreEntryName.Flat -> key
        is StoreEntryName.Layered -> "$key::$entry"
    }

private suspend fun KacheableStore.save(
    entryName: StoreEntryName,
    value: String,
    config: CacheConfig?,
    observation: OperationObservation,
) {
    val started = observation.startTimer()
    try {
        if ((config?.expiryType ?: ExpiryType.none) == ExpiryType.none) {
            set(entryName, value)
        } else {
            when (entryName) {
                is StoreEntryName.Flat -> setValueWithExpire(entryName.key, value, config!!.expiry)
                is StoreEntryName.Layered -> setHashValueWithExpire(entryName.key, entryName.entry, value, config!!.expiry)
            }
        }
        observation.storageWrite(CacheWriteResult.Stored, started)
    } catch (t: Throwable) {
        observation.storageWrite(CacheWriteResult.Failed, started)
        throw t
    }
}

private fun BlockingKacheableStore.save(
    entryName: StoreEntryName,
    value: String,
    config: CacheConfig?,
    observation: OperationObservation,
) {
    val started = observation.startTimer()
    try {
        if ((config?.expiryType ?: ExpiryType.none) == ExpiryType.none) {
            set(entryName, value)
        } else {
            when (entryName) {
                is StoreEntryName.Flat -> setValueWithExpire(entryName.key, value, config!!.expiry)
                is StoreEntryName.Layered -> setHashValueWithExpire(entryName.key, entryName.entry, value, config!!.expiry)
            }
        }
        observation.storageWrite(CacheWriteResult.Stored, started)
    } catch (t: Throwable) {
        observation.storageWrite(CacheWriteResult.Failed, started)
        throw t
    }
}

private data class ObservedValue<R>(
    val value: R,
    val result: CacheOperationResult,
)

private data class DecodedRead<R>(
    val value: R,
)
