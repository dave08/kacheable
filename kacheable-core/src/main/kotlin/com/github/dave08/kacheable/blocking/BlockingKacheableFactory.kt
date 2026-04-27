package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.DefaultGetNameStrategy
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.blocking.internal.BlockingKacheableImpl
import kotlinx.serialization.json.Json

fun BlockingKacheable(
    store: BlockingKacheableStore,
    configs: Map<String, CacheConfig> = emptyMap(),
    getNameStrategy: GetNameStrategy = DefaultGetNameStrategy,
    jsonParser: Json = Json,
): BlockingKacheable = BlockingKacheableImpl(store, configs, getNameStrategy, jsonParser)
