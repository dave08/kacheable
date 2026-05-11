@file:Suppress("DEPRECATION")

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.asCacheNamingStrategy
import com.github.dave08.kacheable.defaultCacheNamingStrategy
import com.github.dave08.kacheable.blocking.internal.BlockingKacheableImpl
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import kotlinx.serialization.json.Json

/**
 * Creates a blocking cache runtime over [store].
 */
fun BlockingKacheable(
    store: BlockingKacheableStore,
    configs: Map<String, CacheConfig> = emptyMap(),
    namingStrategy: CacheNamingStrategy = defaultCacheNamingStrategy(),
    jsonParser: Json = Json,
): BlockingKacheable = BlockingKacheableImpl(store, configs, namingStrategy, jsonParser)

@Deprecated(
    message = "Use the BlockingKacheable factory overload that takes CacheNamingStrategy.",
    replaceWith = ReplaceWith(
        expression = "BlockingKacheable(store, configs, getNameStrategy.asCacheNamingStrategy(), jsonParser)",
        imports = ["com.github.dave08.kacheable.asCacheNamingStrategy"],
    ),
)
fun BlockingKacheable(
    store: BlockingKacheableStore,
    configs: Map<String, CacheConfig> = emptyMap(),
    getNameStrategy: GetNameStrategy,
    jsonParser: Json = Json,
): BlockingKacheable = BlockingKacheable(store, configs, getNameStrategy.asCacheNamingStrategy(), jsonParser)
