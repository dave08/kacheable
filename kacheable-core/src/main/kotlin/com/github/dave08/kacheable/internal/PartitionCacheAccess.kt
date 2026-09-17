package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.CacheLoadContext
import com.github.dave08.kacheable.PartitionCacheEntryRef

internal interface PartitionCacheAccess {
    suspend fun <K, V, P : KeyPart<K>> loadPartition(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: suspend (K, CacheLoadContext<K, V, P>) -> V,
    ): V

    suspend fun <K, V, P : KeyPart<K>> loadPartition(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: suspend () -> V,
    ): V
}
