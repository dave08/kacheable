package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.CacheLoadTimeoutException
import com.github.dave08.kacheable.internal.CacheResultPolicy
import com.github.dave08.kacheable.internal.snapshot.CacheSnapshotCoordinator
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
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
    snapshotCoordinator: CacheSnapshotCoordinator?,
    backgroundScope: () -> CoroutineScope,
    codec: CacheValueCodec<R>,
    missPolicy: CacheMissPolicy<R>,
    refreshPolicy: CacheRefreshPolicy<R>,
    storeResultIf: (R) -> Boolean,
    block: suspend (previous: R?) -> R,
): R {
    val config = configs[cacheName]
    readCachedValue(entryName, config)?.let { result ->
        val cached = CacheResultPolicy.decodeCachedResult(result, config, codec)
        return applyRefreshPolicy(
            cached = cached,
            cacheName = cacheName,
            entryName = entryName,
            store = this,
            config = config,
            loadCoordinator = loadCoordinator,
            backgroundScope = backgroundScope,
            refreshPolicy = refreshPolicy,
            loadAndSave = {
                val blockResult = block(cached)
                saveLoaderResult(entryName, blockResult, config, storeResultIf, codec)
                blockResult
            },
            readFreshCached = { readFreshCachedValue(entryName, config, codec, refreshPolicy) },
        )
    }

    snapshotCoordinator?.restoreEntry(cacheName, entryName)
    readCachedValue(entryName, config)?.let { result ->
        val cached = CacheResultPolicy.decodeCachedResult(result, config, codec)
        return applyRefreshPolicy(
            cached = cached,
            cacheName = cacheName,
            entryName = entryName,
            store = this,
            config = config,
            loadCoordinator = loadCoordinator,
            backgroundScope = backgroundScope,
            refreshPolicy = refreshPolicy,
            loadAndSave = {
                val blockResult = block(cached)
                saveLoaderResult(entryName, blockResult, config, storeResultIf, codec)
                blockResult
            },
            readFreshCached = { readFreshCachedValue(entryName, config, codec, refreshPolicy) },
        )
    }

    val readCached = suspend {
        get(entryName)?.let { CacheResultPolicy.decodeCachedResult(it, config, codec) }
    }
    val loadAndSave = suspend {
        val blockResult = block(null)
        saveLoaderResult(entryName, blockResult, config, storeResultIf, codec)
        blockResult
    }

    return when (missPolicy) {
        is CacheMissPolicy.Load -> loadWithPolicy(
            cacheName = cacheName,
            entryName = entryName,
            store = this,
            config = config,
            loadCoordinator = loadCoordinator,
            readCached = readCached,
            loadAndSave = loadAndSave,
            onFailure = { error ->
                missPolicy.fallbackOnFailure?.invoke(error) ?: throw error
            },
        )

        is CacheMissPolicy.LoadInBackground -> {
            val fallback = missPolicy.fallback()
            backgroundScope().launch {
                try {
                    loadWithPolicy(
                        cacheName = cacheName,
                        entryName = entryName,
                        store = this@invokeAtAddress,
                        config = config,
                        loadCoordinator = loadCoordinator,
                        readCached = readCached,
                        loadAndSave = loadAndSave,
                        onFailure = { throw it },
                    )
                } catch (_: TimeoutCancellationException) {
                    // Background load timeouts are failures, not caller-visible cancellation.
                } catch (t: CancellationException) {
                    throw t
                } catch (_: Throwable) {
                    // Background refresh failures are intentionally not surfaced to the caller.
                }
            }
            fallback
        }
    }
}

private suspend fun <R> KacheableStore.applyRefreshPolicy(
    cached: R,
    cacheName: String,
    entryName: StoreEntryName,
    store: KacheableStore,
    config: CacheConfig?,
    loadCoordinator: CacheLoadCoordinator,
    backgroundScope: () -> CoroutineScope,
    refreshPolicy: CacheRefreshPolicy<R>,
    loadAndSave: suspend () -> R,
    readFreshCached: suspend () -> R?,
): R {
    return when (refreshPolicy) {
        is CacheRefreshPolicy.NeverRefresh -> cached
        is CacheRefreshPolicy.RefreshIf -> {
            if (!refreshPolicy.isStale(cached)) return cached

            if (refreshPolicy.inBackground) {
                backgroundScope().launch {
                    try {
                        loadWithPolicy(
                            cacheName = cacheName,
                            entryName = entryName,
                            store = store,
                            config = config,
                            loadCoordinator = loadCoordinator,
                            readCached = readFreshCached,
                            loadAndSave = loadAndSave,
                            onFailure = { throw it },
                        )
                    } catch (_: TimeoutCancellationException) {
                        // Background refresh timeouts are failures, not caller-visible cancellation.
                    } catch (t: CancellationException) {
                        throw t
                    } catch (_: Throwable) {
                        // Background refresh failures are intentionally not surfaced to the caller.
                    }
                }
                cached
            } else {
                loadWithPolicy(
                    cacheName = cacheName,
                    entryName = entryName,
                    store = store,
                    config = config,
                    loadCoordinator = loadCoordinator,
                    readCached = readFreshCached,
                    loadAndSave = loadAndSave,
                    onFailure = { cached },
                )
            }
        }
    }
}

private suspend fun <R> KacheableStore.readFreshCachedValue(
    entryName: StoreEntryName,
    config: CacheConfig?,
    codec: CacheValueCodec<R>,
    refreshPolicy: CacheRefreshPolicy<R>,
): R? {
    val raw = readCachedValue(entryName, config) ?: return null
    val cached = CacheResultPolicy.decodeCachedResult(raw, config, codec)
    return when (refreshPolicy) {
        is CacheRefreshPolicy.NeverRefresh -> cached
        is CacheRefreshPolicy.RefreshIf -> cached.takeUnless(refreshPolicy.isStale)
    }
}

private suspend fun <R> KacheableStore.saveLoaderResult(
    entryName: StoreEntryName,
    blockResult: R,
    config: CacheConfig?,
    storeResultIf: (R) -> Boolean,
    codec: CacheValueCodec<R>,
) {
    val resultToSave = CacheResultPolicy.encodeResultToSave(
        blockResult,
        config,
        storeResultIf,
        codec,
    )
    resultToSave?.let { save(entryName, it, config) }
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

private suspend fun <R> loadWithPolicy(
    cacheName: String,
    entryName: StoreEntryName,
    store: KacheableStore,
    config: CacheConfig?,
    loadCoordinator: CacheLoadCoordinator,
    readCached: suspend () -> R?,
    loadAndSave: suspend () -> R,
    onFailure: suspend (Throwable) -> R,
): R {
    val resilience = loadCoordinator.resilienceFor(config)
    return try {
        loadCoordinator.load(
            cacheName = cacheName,
            entryKey = entryName.cacheLoadKey,
            store = store,
            config = config,
            readCached = readCached,
            loadAndSave = loadAndSave,
        )
    } catch (t: TimeoutCancellationException) {
        readCached().takeIf { resilience.staleOnTimeout } ?: onFailure(t)
    } catch (t: CacheLoadTimeoutException) {
        readCached().takeIf { resilience.staleOnTimeout } ?: onFailure(t)
    } catch (t: CancellationException) {
        throw t
    } catch (t: Throwable) {
        readCached().takeIf { resilience.staleOnFailure } ?: onFailure(t)
    }
}

internal fun <R> BlockingKacheableStore.invokeAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    configs: Map<String, CacheConfig>,
    codec: CacheValueCodec<R>,
    saveResultIf: (R) -> Boolean,
    block: () -> R,
): R {
    val config = configs[cacheName]
    val result =
        if (entryName is StoreEntryName.Flat && config?.expiryType == ExpiryType.after_access) {
            getValueRefreshingExpire(entryName.key, config.expiry)
        } else {
            get(entryName)
        }

    return if (result == null) {
        val blockResult = block()
        val resultToSave = CacheResultPolicy.encodeResultToSave(blockResult, config, saveResultIf, codec)
        resultToSave?.let {
            save(entryName, it, config)
        }
        blockResult
    } else {
        if (entryName is StoreEntryName.Layered && config?.expiryType == ExpiryType.after_access) {
            setExpire(entryName.key, config.expiry)
        }
        CacheResultPolicy.decodeCachedResult(result, config, codec)
    }
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
) {
    if ((config?.expiryType ?: ExpiryType.none) == ExpiryType.none) {
        set(entryName, value)
        return
    }

    when (entryName) {
        is StoreEntryName.Flat -> setValueWithExpire(entryName.key, value, config!!.expiry)
        is StoreEntryName.Layered -> setHashValueWithExpire(entryName.key, entryName.entry, value, config!!.expiry)
    }
}

private fun BlockingKacheableStore.save(
    entryName: StoreEntryName,
    value: String,
    config: CacheConfig?,
) {
    if ((config?.expiryType ?: ExpiryType.none) == ExpiryType.none) {
        set(entryName, value)
        return
    }

    when (entryName) {
        is StoreEntryName.Flat -> setValueWithExpire(entryName.key, value, config!!.expiry)
        is StoreEntryName.Layered -> setHashValueWithExpire(entryName.key, entryName.entry, value, config!!.expiry)
    }
}
