package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.cacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class BlockingKacheableImpl(
    private val runtime: BlockingTypedCacheRuntime,
    private val jsonParser: Json
) : BlockingKacheable, BlockingTypedCacheRuntime by runtime {
    constructor(
        store: com.github.dave08.kacheable.blocking.store.BlockingKacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        jsonParser: Json,
    ) : this(DefaultBlockingTypedCacheRuntime(store, configs, namingStrategy), jsonParser)

    override fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R = runtime.invoke(
        name = name,
        codec = cacheValueCodec(type, jsonParser),
        params = params,
        saveResultIf = saveResultIf,
        block = block,
    )
}
