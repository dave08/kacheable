@file:Suppress("DEPRECATION")

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.KacheableImpl
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.serialization.json.Json

/**
 * Creates a suspended cache runtime over [store].
 */
fun Kacheable(
    store: KacheableStore,
    configs: Map<String, CacheConfig> = emptyMap(),
    namingStrategy: CacheNamingStrategy = defaultCacheNamingStrategy(),
    jsonParser: Json = Json,
    defaultResilience: CacheResilienceConfig = CacheResilienceConfig(),
): Kacheable = KacheableImpl(store, configs, namingStrategy, jsonParser, defaultResilience)

@Deprecated(
    message = "Use the Kacheable factory overload that takes CacheNamingStrategy.",
    replaceWith = ReplaceWith(
        expression = "Kacheable(store, configs, getNameStrategy.asCacheNamingStrategy(), jsonParser)",
        imports = ["com.github.dave08.kacheable.asCacheNamingStrategy"],
    ),
)
fun Kacheable(
    store: KacheableStore,
    configs: Map<String, CacheConfig> = emptyMap(),
    getNameStrategy: GetNameStrategy,
    jsonParser: Json = Json,
    defaultResilience: CacheResilienceConfig = CacheResilienceConfig(),
): Kacheable = Kacheable(store, configs, getNameStrategy.asCacheNamingStrategy(), jsonParser, defaultResilience)
