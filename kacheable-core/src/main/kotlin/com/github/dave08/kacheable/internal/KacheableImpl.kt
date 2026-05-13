package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.internal.storage.TypedStorage
import com.github.dave08.kacheable.internal.storage.TypedStorages
import com.github.dave08.kacheable.internal.storage.hash.HashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.set.SetTypedStorage
import com.github.dave08.kacheable.internal.storage.string.StringTypedStorage
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.cacheValueCodec
import com.github.dave08.kacheable.store.DistributedSingleFlightStore
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class KacheableImpl(
    private val storages: TypedStorages,
    private val jsonParser: Json,
) : Kacheable {
    constructor(
        store: KacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        jsonParser: Json,
        defaultResilience: CacheResilienceConfig,
    ) : this(
        storages = createTypedStorages(store, configs, namingStrategy, defaultResilience),
        jsonParser = jsonParser,
    ) {
        validateResilience(store, configs, defaultResilience)
    }

    override suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R =
        storages.string.invalidate(*keys, block = block)

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<*>) {
        @Suppress("UNCHECKED_CAST")
        (storages.any(entryRef.storage) as TypedStorage<CacheStorage>).invalidate(entryRef as StoredCacheEntryRef<CacheStorage>)
    }

    override suspend fun invalidate(partRef: CacheEntryPartRef) {
        @Suppress("UNCHECKED_CAST")
        (storages.any(partRef.storage) as TypedStorage<CacheStorage>).invalidate(partRef as StoredCachePartRef<CacheStorage>)
    }

    override suspend fun invalidate(allRef: StoredCacheAllRef<*>) {
        @Suppress("UNCHECKED_CAST")
        (storages.any(allRef.storage) as TypedStorage<CacheStorage>).invalidate(allRef as StoredCacheAllRef<CacheStorage>)
    }

    override suspend fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        storages.set.invalidate(entryRef, returnView)
    }

    override suspend fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        storages.set.invalidate(partRef, returnView)
    }

    override suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = storages.string.invoke(
        name = name,
        codec = cacheValueCodec(type, jsonParser),
        params = params,
        saveResultIf = cacheIf,
        block = block,
    )

    override suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        cacheIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        @Suppress("UNCHECKED_CAST")
        return (storages.any(entryRef.storage) as TypedStorage<S>).invoke(entryRef, returnView, cacheIf, block)
    }

    override suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = storages.string.invoke(
        name = name,
        codec = codec,
        params = params,
        saveResultIf = cacheIf,
        block = block,
    )

    companion object {
        private fun createTypedStorages(
            store: KacheableStore,
            configs: Map<String, CacheConfig>,
            namingStrategy: CacheNamingStrategy,
            defaultResilience: CacheResilienceConfig,
        ): TypedStorages {
            val loadCoordinator = CacheLoadCoordinator(defaultResilience)
            return TypedStorages(
                string = StringTypedStorage(store, configs, loadCoordinator, namingStrategy),
                hashMap = HashMapTypedStorage(store, configs, loadCoordinator, namingStrategy),
                set = SetTypedStorage(store, configs, loadCoordinator, namingStrategy),
            )
        }
    }
}

private fun validateResilience(
    store: KacheableStore,
    configs: Map<String, CacheConfig>,
    defaultResilience: CacheResilienceConfig,
) {
    val redisSingleFlightConfigured =
        defaultResilience.singleFlight == SingleFlightMode.Redis ||
            configs.values.any { it.resilience?.singleFlight == SingleFlightMode.Redis }

    if (redisSingleFlightConfigured && store !is DistributedSingleFlightStore) {
        throw IllegalArgumentException(
            "Redis single-flight requires a store that implements DistributedSingleFlightStore.",
        )
    }
}
