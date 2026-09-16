package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.store.CacheCodec

internal object CacheResultPolicy {
    fun <R> encodeResultToSave(
        blockResult: R,
        config: CacheConfig?,
        saveResultIf: (R) -> Boolean,
        codec: CacheCodec<R>,
    ): String? = when {
        blockResult == null && config?.nullPlaceholder != null -> config.nullPlaceholder
        blockResult == null || !saveResultIf(blockResult) -> null
        else -> codec.encode(blockResult)
    }

    @Suppress("UNCHECKED_CAST")
    fun <R> decodeCachedResult(
        cachedResult: String,
        config: CacheConfig?,
        codec: CacheCodec<R>,
    ): R {
        return if (config?.nullPlaceholder != null && cachedResult == config.nullPlaceholder)
            null as R
        else
            codec.decode(cachedResult)
    }
}
