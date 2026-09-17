package com.github.dave08.kacheable

/** A selection of existing entries. No additional cache key or collection value is created. */
class CacheManyRef<K, V, out P : KeyPart<K>> internal constructor(
    keys: List<K>,
    val keyPart: P,
    internal val entry: (K) -> CacheEntryRef<V>,
) {
    val keys: List<K> = keys.toList()
}

/**
 * Loads unresolved entries together. A returned key is resolved, including an explicit null.
 * Omitted keys remain unresolved. Resolved values are stored independently according to [cacheIf].
 * The returned map contains requested resolved keys in first-request order.
 */
suspend operator fun <K, V, P : KeyPart<K>> Kacheable.invoke(
    ref: CacheManyRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: suspend (List<K>, CacheLoadContext<K, V, P>) -> Map<K, V>,
): Map<K, V> = invoke(ref, CacheMissPolicy.load(), CacheRefreshPolicy.neverRefresh(), cacheIf, block)

suspend fun <K, V, P : KeyPart<K>> Kacheable.cache(
    ref: CacheManyRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: suspend (List<K>, CacheLoadContext<K, V, P>) -> Map<K, V>,
): Map<K, V> = invoke(ref, cacheIf, block)

suspend fun <K, V, P : KeyPart<K>> Kacheable.cache(
    ref: CacheManyRef<K, V, P>,
    missPolicy: CacheMissPolicy<V>,
    refreshPolicy: CacheRefreshPolicy<V> = CacheRefreshPolicy.neverRefresh(),
    storeResultIf: (V) -> Boolean = { true },
    block: suspend (List<K>, CacheLoadContext<K, V, P>) -> Map<K, V>,
): Map<K, V> = invoke(ref, missPolicy, refreshPolicy, storeResultIf, block)

/** A loader completed without resolving this requested key. It supplied no underlying cause. */
class UnresolvedCacheKeyException(val key: Any?) : RuntimeException("Cache loader did not resolve key '$key'.")
