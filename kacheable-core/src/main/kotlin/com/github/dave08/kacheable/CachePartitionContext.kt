package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.PartitionCacheAccess
import com.github.dave08.kacheable.store.CacheCodec

/** Distinguishes a missing entry from a published nullable value. */
sealed interface CachePartitionEntry<out V> {
    data class Present<V>(val value: V) : CachePartitionEntry<V>
    data object Missing : CachePartitionEntry<Nothing>
}

/**
 * Lazy published sibling reads during one loader attempt. Reads never invoke loaders.
 * Concurrent changes abort the attempt; reads after the loader completes are rejected.
 * [P] retains the declared key capability, enabling enumeration only for enumerable keys.
 */
abstract class CachePartitionContext<K, V, out P : KeyPart<K>> internal constructor() {
    internal abstract val keyPart: P
    abstract suspend fun entry(key: K): CachePartitionEntry<V>
    abstract suspend fun entries(keys: Iterable<K>): Map<K, V>
    internal abstract suspend fun enumerateEntries(codec: CacheCodec<K>): Map<K, V>
    internal abstract suspend fun enumerateKeys(codec: CacheCodec<K>): Set<K>
}

/** Materializes the published partition. Prefer selected reads for large values. */
suspend fun <K, V> CachePartitionContext<K, V, EnumerableKeyPart<K>>.entries(): Map<K, V> =
    enumerateEntries(keyPart.codec)

/** Reads typed key metadata without fetching sibling payloads. */
suspend fun <K, V> CachePartitionContext<K, V, EnumerableKeyPart<K>>.keys(): Set<K> =
    enumerateKeys(keyPart.codec)

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
    block: suspend (K, CachePartitionContext<K, V, P>) -> V,
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
    block: suspend (K, CachePartitionContext<K, V, P>) -> V,
): V = invoke(entryRef, cacheIf, block)

suspend fun <K, V, P : KeyPart<K>> Kacheable.cache(
    entryRef: PartitionCacheEntryRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: suspend () -> V,
): V = invoke(entryRef, cacheIf, block)

private fun Kacheable.partitionAccess(): PartitionCacheAccess = this as? PartitionCacheAccess
    ?: throw UnsupportedOperationException("This cache runtime does not support partition contexts.")
