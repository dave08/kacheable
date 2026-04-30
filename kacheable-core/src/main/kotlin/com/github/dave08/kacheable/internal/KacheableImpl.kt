package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.cacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class KacheableImpl(
    private val runtime: TypedCacheRuntime,
    private val jsonParser: Json,
) : Kacheable, TypedCacheRuntime by runtime {
    constructor(
        store: com.github.dave08.kacheable.store.KacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        jsonParser: Json,
    ) : this(DefaultTypedCacheRuntime(store, configs, namingStrategy), jsonParser)

    override suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = runtime.invoke(
        name = name,
        codec = cacheValueCodec(type, jsonParser),
        params = params,
        saveResultIf = saveResultIf,
        block = block,
    )
}
