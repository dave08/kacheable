package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheEntry
import com.github.dave08.kacheable.EnumerableKeyPart
import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.store.CacheCodec

/**
 * Reads cached entries during one blocking scalar or batch loader attempt.
 * Reads never invoke loaders. Guarded partitions also validate the attempt's version.
 */
abstract class BlockingCacheLoadContext<K, V, out P : KeyPart<K>> internal constructor() {
    internal abstract val keyPart: P
    abstract fun entry(key: K): CacheEntry<V>
    abstract fun entries(keys: Iterable<K>): Map<K, V>
    internal open fun enumerateEntries(codec: CacheCodec<K>): Map<K, V> =
        throw UnsupportedOperationException("This cache does not support stored-key enumeration.")
    internal open fun enumerateKeys(codec: CacheCodec<K>): Set<K> =
        throw UnsupportedOperationException("This cache does not support stored-key enumeration.")
}

/** Enumerates a supported partition whose logical keys have a codec. */
fun <K, V> BlockingCacheLoadContext<K, V, EnumerableKeyPart<K>>.keys(): Set<K> =
    enumerateKeys(keyPart.codec)

fun <K, V> BlockingCacheLoadContext<K, V, EnumerableKeyPart<K>>.entries(): Map<K, V> =
    enumerateEntries(keyPart.codec)
