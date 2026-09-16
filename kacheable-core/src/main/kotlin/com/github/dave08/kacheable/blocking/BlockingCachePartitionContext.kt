package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CachePartitionEntry
import com.github.dave08.kacheable.EnumerableKeyPart
import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.PartitionCacheEntryRef
import com.github.dave08.kacheable.store.CacheCodec

/** Lazy published sibling reads, valid only during one blocking loader attempt. */
abstract class BlockingCachePartitionContext<K, V, out P : KeyPart<K>> internal constructor() {
    internal abstract val keyPart: P
    abstract fun entry(key: K): CachePartitionEntry<V>
    abstract fun entries(keys: Iterable<K>): Map<K, V>
    internal abstract fun enumerateEntries(codec: CacheCodec<K>): Map<K, V>
    internal abstract fun enumerateKeys(codec: CacheCodec<K>): Set<K>
}

/** Enumerates keys only when their declaration supplies a reversible codec. */
fun <K, V> BlockingCachePartitionContext<K, V, EnumerableKeyPart<K>>.keys(): Set<K> =
    enumerateKeys(keyPart.codec)

/** Materializes all published entries; prefer selected reads for large partitions. */
fun <K, V> BlockingCachePartitionContext<K, V, EnumerableKeyPart<K>>.entries(): Map<K, V> =
    enumerateEntries(keyPart.codec)

internal interface BlockingPartitionCacheAccess {
    fun <K, V, P : KeyPart<K>> loadPartition(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: (K, BlockingCachePartitionContext<K, V, P>) -> V,
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
    block: (K, BlockingCachePartitionContext<K, V, P>) -> V,
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
    block: (K, BlockingCachePartitionContext<K, V, P>) -> V,
): V = invoke(entryRef, cacheIf, block)

fun <K, V, P : KeyPart<K>> BlockingKacheable.cache(
    entryRef: PartitionCacheEntryRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: () -> V,
): V = invoke(entryRef, cacheIf, block)
