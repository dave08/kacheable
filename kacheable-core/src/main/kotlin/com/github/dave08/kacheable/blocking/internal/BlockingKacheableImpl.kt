package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorage
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorages
import com.github.dave08.kacheable.internal.storage.hash.BlockingHashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.set.BlockingSetTypedStorage
import com.github.dave08.kacheable.internal.storage.string.BlockingStringTypedStorage
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.cacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class BlockingKacheableImpl(
    private val storages: BlockingTypedStorages,
    private val jsonParser: Json
) : BlockingKacheable {
    constructor(
        store: BlockingKacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        jsonParser: Json,
    ) : this(
        storages = BlockingTypedStorages(
            string = BlockingStringTypedStorage(store, configs, namingStrategy),
            hashMap = BlockingHashMapTypedStorage(store, configs, namingStrategy),
            set = BlockingSetTypedStorage(store, configs, namingStrategy),
        ),
        jsonParser = jsonParser,
    )

    override fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R =
        storages.string.invalidate(*keys, block = block)

    override fun invalidate(entryRef: StoredCacheEntryRef<*>) {
        @Suppress("UNCHECKED_CAST")
        (storages.any(entryRef.storage) as BlockingTypedStorage<CacheStorage>).invalidate(entryRef as StoredCacheEntryRef<CacheStorage>)
    }

    override fun invalidate(partRef: CacheEntryPartRef) {
        @Suppress("UNCHECKED_CAST")
        (storages.any(partRef.storage) as BlockingTypedStorage<CacheStorage>).invalidate(partRef as StoredCachePartRef<CacheStorage>)
    }

    override fun invalidate(allRef: StoredCacheAllRef<*>) {
        @Suppress("UNCHECKED_CAST")
        (storages.any(allRef.storage) as BlockingTypedStorage<CacheStorage>).invalidate(allRef as StoredCacheAllRef<CacheStorage>)
    }

    override fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        storages.set.invalidate(entryRef, returnView)
    }

    override fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        storages.set.invalidate(partRef, returnView)
    }

    override fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: () -> R
    ): R = storages.string.invoke(
        name = name,
        codec = cacheValueCodec(type, jsonParser),
        params = params,
        saveResultIf = cacheIf,
        block = block,
    )

    override fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        cacheIf: (R) -> Boolean,
        block: () -> R,
    ): R {
        @Suppress("UNCHECKED_CAST")
        return (storages.any(entryRef.storage) as BlockingTypedStorage<S>).invoke(entryRef, returnView, cacheIf, block)
    }

    override fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: () -> R,
    ): R = storages.string.invoke(
        name = name,
        codec = codec,
        params = params,
        saveResultIf = cacheIf,
        block = block,
    )
}
