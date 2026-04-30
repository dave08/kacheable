package com.github.dave08.kacheable.internal.storage.hash

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.storage.TypedStorage
import com.github.dave08.kacheable.internal.storage.invokeAtAddress
import com.github.dave08.kacheable.store.KacheableStore

@OptIn(ExperimentalKacheableApi::class)
internal class HashMapTypedStorage(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    namingStrategy: CacheNamingStrategy,
) : TypedStorage<CacheStorage.HashMap> {
    override val storage: CacheStorage.HashMap = CacheStorage.HashMap
    private val entryNamer = CacheEntryNamer(namingStrategy)

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<CacheStorage.HashMap>) {
        HashMapStorageStrategy.invalidate(store, entryNamer, entryRef.name, entryRef.cacheArgs, null) {}
    }

    override suspend fun invalidate(partRef: StoredCachePartRef<CacheStorage.HashMap>) {
        HashMapStorageStrategy.invalidate(
            store,
            entryNamer,
            partRef.name,
            partRef.cacheArgs,
            partRef.secondaryPatternPartArgs,
        ) {}
    }

    override suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.HashMap>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = store.invokeAtAddress(
        entryName = HashMapStorageStrategy.storeEntryName(entryNamer.nameEntry(entryRef.name, entryRef.cacheArgs)),
        cacheName = entryRef.name,
        configs = configs,
        codec = returnsAs.codec,
        saveResultIf = saveResultIf,
        block = block,
    )
}
