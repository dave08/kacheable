package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.PartitionCacheEntryRef

internal interface BlockingPartitionCacheAccess {
    fun <K, V, P : KeyPart<K>> loadPartition(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: (K, BlockingCacheLoadContext<K, V, P>) -> V,
    ): V
    fun <K, V, P : KeyPart<K>> loadPartition(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: () -> V,
    ): V

}

operator fun <K, V, P : KeyPart<K>> BlockingKacheable.invoke(
    entryRef: PartitionCacheEntryRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: (K, BlockingCacheLoadContext<K, V, P>) -> V,
): V = (this as? BlockingPartitionCacheAccess
    ?: throw UnsupportedOperationException("This cache runtime does not support partition contexts."))
    .loadPartition(entryRef, cacheIf, block)

operator fun <K, V, P : KeyPart<K>> BlockingKacheable.invoke(
    entryRef: PartitionCacheEntryRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: () -> V,
): V = if (this is BlockingPartitionCacheAccess) {
    loadPartition(entryRef, cacheIf, block)
} else {
    invoke(entryRef.entryRef, entryRef.returnView, cacheIf, block)
}

fun <K, V, P : KeyPart<K>> BlockingKacheable.cache(
    entryRef: PartitionCacheEntryRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: (K, BlockingCacheLoadContext<K, V, P>) -> V,
): V = invoke(entryRef, cacheIf, block)

fun <K, V, P : KeyPart<K>> BlockingKacheable.cache(
    entryRef: PartitionCacheEntryRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: () -> V,
): V = invoke(entryRef, cacheIf, block)
