package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.CacheLoadTimeoutException
import com.github.dave08.kacheable.internal.CacheResultPolicy
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

internal suspend fun <R> KacheableStore.invokeAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    configs: Map<String, CacheConfig>,
    loadCoordinator: CacheLoadCoordinator,
    codec: CacheValueCodec<R>,
    saveResultIf: (R) -> Boolean,
    block: suspend () -> R,
): R {
    val config = configs[cacheName]
    val resilience = loadCoordinator.resilienceFor(config)
    val result =
        if (entryName is StoreEntryName.Flat && config?.expiryType == ExpiryType.after_access) {
            getValueRefreshingExpire(entryName.key, config.expiry)
        } else {
            get(entryName)
        }

    return if (result == null) {
        val readCached = suspend {
            get(entryName)?.let { CacheResultPolicy.decodeCachedResult(it, config, codec) }
        }

        try {
            loadCoordinator.load(
                cacheName = cacheName,
                entryKey = entryName.cacheLoadKey,
                store = this,
                config = config,
                readCached = readCached,
            ) {
                val blockResult = block()
                val resultToSave = CacheResultPolicy.encodeResultToSave(blockResult, config, saveResultIf, codec)
                resultToSave?.let { save(entryName, it, config) }
                blockResult
            }
        } catch (t: TimeoutCancellationException) {
            readCached().takeIf { resilience.staleOnTimeout } ?: throw t
        } catch (t: CacheLoadTimeoutException) {
            readCached().takeIf { resilience.staleOnTimeout } ?: throw t
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            readCached().takeIf { resilience.staleOnFailure } ?: throw t
        }
    } else {
        if (entryName is StoreEntryName.Layered && config?.expiryType == ExpiryType.after_access) {
            setExpire(entryName.key, config.expiry)
        }
        CacheResultPolicy.decodeCachedResult(result, config, codec)
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
