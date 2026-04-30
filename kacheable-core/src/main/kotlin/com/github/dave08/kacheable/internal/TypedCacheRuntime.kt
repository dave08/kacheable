package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.storage.HashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.SetTypedStorage
import com.github.dave08.kacheable.internal.storage.StringTypedStorage
import com.github.dave08.kacheable.internal.storage.TypedStorage
import com.github.dave08.kacheable.internal.storage.TypedStorages
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.KacheableStore

@OptIn(ExperimentalKacheableApi::class)
internal interface TypedCacheRuntime {
    suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R

    suspend fun invalidate(entryRef: StoredCacheEntryRef<*>)

    suspend fun invalidate(partRef: CacheEntryPartRef)

    suspend fun <E : Enum<E>> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    )

    suspend fun <E : Enum<E>> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    )

    suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R

    suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R
}

@OptIn(ExperimentalKacheableApi::class)
internal class DefaultTypedCacheRuntime(
    private val storages: TypedStorages,
) : TypedCacheRuntime {
    constructor(
        store: KacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
    ) : this(
        TypedStorages(
            string = StringTypedStorage(store, configs, namingStrategy),
            hashMap = HashMapTypedStorage(store, configs, namingStrategy),
            set = SetTypedStorage(store, configs, namingStrategy),
        ),
    )

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

    override suspend fun <E : Enum<E>> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    ) {
        storages.set.invalidate(entryRef, returnsAs)
    }

    override suspend fun <E : Enum<E>> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    ) {
        storages.set.invalidate(partRef, returnsAs)
    }

    override suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = storages.string.invoke(name, codec, params, saveResultIf, block)

    override suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        @Suppress("UNCHECKED_CAST")
        return (storages.any(entryRef.storage) as TypedStorage<S>).invoke(entryRef, returnsAs, saveResultIf, block)
    }
}
