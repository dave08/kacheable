package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.KacheableImpl
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.serialization.json.Json

fun Kacheable(
    store: KacheableStore,
    configs: Map<String, CacheConfig> = emptyMap(),
    getNameStrategy: GetNameStrategy = DefaultGetNameStrategy,
    jsonParser: Json = Json,
): Kacheable = KacheableImpl(store, configs, getNameStrategy, jsonParser)
