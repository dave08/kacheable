package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheLoadResult
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
import com.github.dave08.kacheable.internal.BlockingLoadConcurrencyCoordinator
import com.github.dave08.kacheable.internal.CacheResultPolicy
import com.github.dave08.kacheable.internal.OperationObservation
import com.github.dave08.kacheable.internal.snapshot.CacheSnapshotCoordinator
import com.github.dave08.kacheable.store.CacheCodec
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CoroutineScope

internal suspend fun <R> KacheableStore.invokeAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    configs: Map<String, CacheConfig>,
    loadCoordinator: CacheLoadCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    snapshotCoordinator: CacheSnapshotCoordinator?,
    backgroundScope: () -> CoroutineScope,
    codec: CacheCodec<R>,
    missPolicy: CacheMissPolicy<R>,
    refreshPolicy: CacheRefreshPolicy<R>,
    storeResultIf: (R) -> Boolean,
    observation: OperationObservation,
    block: suspend (previous: R?) -> R,
): R = invokeCacheLifecycle(
    cacheName = cacheName,
    entryKey = entryName.cacheLoadKey,
    store = this,
    config = configs[cacheName],
    loadCoordinator = loadCoordinator,
    loadConcurrency = loadConcurrency,
    backgroundScope = backgroundScope,
    missPolicy = missPolicy,
    refreshPolicy = refreshPolicy,
    observation = observation,
    restoreCached = { snapshotCoordinator?.restoreEntry(cacheName, entryName) },
    readCached = { attempt ->
        readObservedRawValue(entryName, configs[cacheName], observation, attempt)
            ?.let { CachedValue(CacheResultPolicy.decodeCachedResult(it, configs[cacheName], codec)) }
    },
    save = { result ->
        saveLoaderResult(entryName, result, configs[cacheName], storeResultIf, codec, observation)
    },
    load = block,
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
): R = invokeCacheLifecycle(
    cacheName = cacheName,
    entryKey = entryName.cacheLoadKey,
    store = this,
    config = configs[cacheName],
    loadCoordinator = loadCoordinator,
    loadConcurrency = loadConcurrency,
    backgroundScope = backgroundScope,
    missPolicy = missPolicy,
    refreshPolicy = refreshPolicy,
    observation = observation,
    restoreCached = { snapshotCoordinator?.restoreEntry(cacheName, entryName) },
    readCached = { attempt ->
        readDecodedCachedValue(entryName, configs[cacheName], returnView, observation, attempt)
    },
    save = { result ->
        saveLoaderResult(entryName, result, configs[cacheName], storeResultIf, returnView.codec, observation)
    },
    load = block,
)

private suspend fun <R> KacheableStore.readDecodedCachedValue(
    entryName: StoreEntryName,
    config: CacheConfig?,
    returnView: CacheReturn<R, *>,
    observation: OperationObservation,
    attempt: CacheReadAttempt,
): CachedValue<R>? {
    val raw = readObservedRawValue(entryName, config, observation, attempt) ?: return null
    return CachedValue(CacheResultPolicy.decodeCachedResult(raw, config, returnView.codec))
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
    codec: CacheCodec<R>,
    observation: OperationObservation,
) {
    val resultToSave = CacheResultPolicy.encodeResultToSave(blockResult, config, storeResultIf, codec)
    if (resultToSave == null) {
        observation.storageWrite(CacheWriteResult.Skipped, observation.startTimer())
    } else {
        save(entryName, resultToSave, config, observation)
    }
}

internal fun <R> BlockingKacheableStore.invokeAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    configs: Map<String, CacheConfig>,
    loadCoordinator: BlockingLoadConcurrencyCoordinator,
    loadConcurrency: LoadConcurrencyGroup?,
    codec: CacheCodec<R>,
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
    decode: (String) -> R,
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
        val decoded = CachedValue(decode(value))
        observation.complete(CacheOperationResult.CachedValue)
        return decoded.value
    }

    val loaded = loadCoordinator.withPermit(cacheName, loadConcurrency, observation) {
        observation.loaderStarted(CacheLoadTrigger.Miss, CacheExecution.Foreground)
        val loadStarted = observation.startTimer()
        try {
            block().also {
                observation.loaderCompleted(
                    CacheLoadTrigger.Miss,
                    CacheExecution.Foreground,
                    CacheLoadResult.Success,
                    loadStarted,
                )
            }
        } catch (t: Throwable) {
            observation.loaderCompleted(
                CacheLoadTrigger.Miss,
                CacheExecution.Foreground,
                CacheLoadResult.Failure,
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
