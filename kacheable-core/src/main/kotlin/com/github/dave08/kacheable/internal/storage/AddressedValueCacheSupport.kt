package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.internal.CacheResultPolicy
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.KacheableStore

internal suspend fun <R> KacheableStore.invokeAtAddress(
    entryName: StoreEntryName,
    cacheName: String,
    configs: Map<String, CacheConfig>,
    codec: CacheValueCodec<R>,
    saveResultIf: (R) -> Boolean,
    block: suspend () -> R,
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
            if (entryName is StoreEntryName.Flat && (config?.expiryType ?: ExpiryType.none) != ExpiryType.none) {
                setValueWithExpire(entryName.key, it, config!!.expiry)
            } else {
                mutate {
                    set(entryName, it)
                    if ((config?.expiryType ?: ExpiryType.none) != ExpiryType.none) {
                        setExpire(
                            when (entryName) {
                                is StoreEntryName.Flat -> entryName.key
                                is StoreEntryName.Layered -> entryName.key
                            },
                            config!!.expiry,
                        )
                    }
                }
            }
        }
        blockResult
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
            if (entryName is StoreEntryName.Flat && (config?.expiryType ?: ExpiryType.none) != ExpiryType.none) {
                setValueWithExpire(entryName.key, it, config!!.expiry)
            } else {
                mutate {
                    set(entryName, it)
                    if ((config?.expiryType ?: ExpiryType.none) != ExpiryType.none) {
                        setExpire(
                            when (entryName) {
                                is StoreEntryName.Flat -> entryName.key
                                is StoreEntryName.Layered -> entryName.key
                            },
                            config!!.expiry,
                        )
                    }
                }
            }
        }
        blockResult
    } else {
        if (entryName is StoreEntryName.Layered && config?.expiryType == ExpiryType.after_access) {
            setExpire(entryName.key, config.expiry)
        }
        CacheResultPolicy.decodeCachedResult(result, config, codec)
    }
}
