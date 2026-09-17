package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.PartitionCacheAccess

/** Retains both the requested key and its declaration's capabilities. */
class PartitionCacheEntryRef<K, V, out P : KeyPart<K>> internal constructor(
    ref: CacheEntryRef<V>,
    val key: K,
    internal val sibling: (K) -> CacheEntryRef<V>,
    val keyPart: P,
) : CacheEntryRef<V>(ref.entryRef, ref.returnView)

/** Loads one missing entry at a time with lazy access to published siblings. */
suspend operator fun <K, V, P : KeyPart<K>> Kacheable.invoke(
    entryRef: PartitionCacheEntryRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: suspend (K, CacheLoadContext<K, V, P>) -> V,
): V = partitionAccess().loadPartition(entryRef, cacheIf, block)

/** Preserves ordinary zero-argument calls and configured partition publication guarantees. */
suspend operator fun <K, V, P : KeyPart<K>> Kacheable.invoke(
    entryRef: PartitionCacheEntryRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: suspend () -> V,
): V = if (this is PartitionCacheAccess) {
    loadPartition(entryRef, cacheIf, block)
} else {
    invoke(entryRef.entryRef, entryRef.returnView, cacheIf, block)
}

suspend fun <K, V, P : KeyPart<K>> Kacheable.cache(
    entryRef: PartitionCacheEntryRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: suspend (K, CacheLoadContext<K, V, P>) -> V,
): V = invoke(entryRef, cacheIf, block)

suspend fun <K, V, P : KeyPart<K>> Kacheable.cache(
    entryRef: PartitionCacheEntryRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: suspend () -> V,
): V = invoke(entryRef, cacheIf, block)

private fun Kacheable.partitionAccess(): PartitionCacheAccess = this as? PartitionCacheAccess
    ?: throw UnsupportedOperationException("This cache runtime does not support partition contexts.")
