package com.github.dave08.kacheable.internal.storage.string

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.storage.TypedStorage
import com.github.dave08.kacheable.internal.storage.delete
import com.github.dave08.kacheable.internal.storage.invokeAtAddress
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.KacheableStore

@OptIn(ExperimentalKacheableApi::class)
internal class StringTypedStorage(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    namingStrategy: CacheNamingStrategy,
) : TypedStorage<CacheStorage.String> {
    override val storage: CacheStorage.String = CacheStorage.String
    private val entryNamer = CacheEntryNamer(namingStrategy)

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<CacheStorage.String>) {
        StringStorageStrategy.invalidate(store, entryNamer, entryRef.name, entryRef.cacheArgs, null) {}
    }

    override suspend fun invalidate(partRef: StoredCachePartRef<CacheStorage.String>) {
        StringStorageStrategy.invalidate(
            store,
            entryNamer,
            partRef.name,
            partRef.cacheArgs,
            partRef.secondaryPatternPartArgs,
        ) {}
    }

    override suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.String>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = store.invokeAtAddress(
        entryName = StringStorageStrategy.storeEntryName(entryNamer.nameEntry(entryRef.name, entryRef.cacheArgs)),
        cacheName = entryRef.name,
        configs = configs,
        codec = returnsAs.codec,
        saveResultIf = saveResultIf,
        block = block,
    )

    suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        params: Array<out Any?>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = store.invokeAtAddress(
        entryName = StringStorageStrategy.storeEntryName(entryNamer.nameEntry(name, params)),
        cacheName = name,
        configs = configs,
        codec = codec,
        saveResultIf = saveResultIf,
        block = block,
    )

    suspend fun <R> invalidate(
        vararg keys: Pair<String, List<Any>>,
        block: suspend () -> R,
    ): R {
        keys.forEach { (name, params) ->
            store.delete(StringStorageStrategy.storeEntryName(entryNamer.nameEntry(name, params.toTypedArray())).key)
        }
        return block()
    }
}
