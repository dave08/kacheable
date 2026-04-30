package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorage
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorages
import com.github.dave08.kacheable.internal.storage.hash.BlockingHashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.set.BlockingSetTypedStorage
import com.github.dave08.kacheable.internal.storage.string.BlockingStringTypedStorage
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.store.CacheValueCodec

@OptIn(ExperimentalKacheableApi::class)
internal interface BlockingTypedCacheRuntime {
    fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R

    fun invalidate(entryRef: StoredCacheEntryRef<*>)

    fun invalidate(partRef: CacheEntryPartRef)

    fun <E : Enum<E>> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    )

    fun <E : Enum<E>> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    )

    fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R

    fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R
}

@OptIn(ExperimentalKacheableApi::class)
internal class DefaultBlockingTypedCacheRuntime(
    private val storages: BlockingTypedStorages,
) : BlockingTypedCacheRuntime {
    constructor(
        store: BlockingKacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
    ) : this(
        BlockingTypedStorages(
            string = BlockingStringTypedStorage(store, configs, namingStrategy),
            hashMap = BlockingHashMapTypedStorage(store, configs, namingStrategy),
            set = BlockingSetTypedStorage(store, configs, namingStrategy),
        ),
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

    override fun <E : Enum<E>> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    ) {
        storages.set.invalidate(entryRef, returnsAs)
    }

    override fun <E : Enum<E>> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    ) {
        storages.set.invalidate(partRef, returnsAs)
    }

    override fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R = storages.string.invoke(name, codec, params, saveResultIf, block)

    override fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R {
        @Suppress("UNCHECKED_CAST")
        return (storages.any(entryRef.storage) as BlockingTypedStorage<S>).invoke(entryRef, returnsAs, saveResultIf, block)
    }
}
