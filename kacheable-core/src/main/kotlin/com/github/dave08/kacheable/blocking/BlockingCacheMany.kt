package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheManyRef
import com.github.dave08.kacheable.KeyPart

fun <K, V, P : KeyPart<K>> BlockingKacheable.cache(
    ref: CacheManyRef<K, V, P>,
    cacheIf: (V) -> Boolean = { true },
    block: (List<K>, BlockingCacheLoadContext<K, V, P>) -> Map<K, V>,
): Map<K, V> = invoke(ref, cacheIf, block)
