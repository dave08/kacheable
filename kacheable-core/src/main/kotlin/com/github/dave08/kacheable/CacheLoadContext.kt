package com.github.dave08.kacheable

import com.github.dave08.kacheable.store.CacheCodec

/**
 * Reads cached entries during one scalar or batch loader attempt. Reads never invoke loaders.
 * Contexts expire when the loader returns. Guarded partitions additionally validate the
 * generation and revision read by the attempt; ordinary contexts use ordinary cache consistency.
 * [P] retains the key declaration's capability for supported stored-key enumeration.
 */
abstract class CacheLoadContext<K, V, out P : KeyPart<K>> internal constructor() {
    internal abstract val keyPart: P
    abstract suspend fun entry(key: K): CacheEntry<V>
    abstract suspend fun entries(keys: Iterable<K>): Map<K, V>
    internal open suspend fun enumerateEntries(codec: CacheCodec<K>): Map<K, V> =
        throw UnsupportedOperationException("This cache does not support stored-key enumeration.")
    internal open suspend fun enumerateKeys(codec: CacheCodec<K>): Set<K> =
        throw UnsupportedOperationException("This cache does not support stored-key enumeration.")
}

/** Enumerates a supported partition whose logical keys have a codec. */
suspend fun <K, V> CacheLoadContext<K, V, EnumerableKeyPart<K>>.keys(): Set<K> =
    enumerateKeys(keyPart.codec)

suspend fun <K, V> CacheLoadContext<K, V, EnumerableKeyPart<K>>.entries(): Map<K, V> =
    enumerateEntries(keyPart.codec)
