package com.github.dave08.kacheable.internal.storage.hash

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorage
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.storage.invokeAtAddress
import com.github.dave08.kacheable.store.CacheValueCodec

internal class BlockingHashMapTypedStorage(
    private val store: BlockingKacheableStore,
    private val configs: Map<String, CacheConfig>,
    namingStrategy: CacheNamingStrategy,
) : BlockingTypedStorage<CacheStorage.HashMap> {
    override val storage: CacheStorage.HashMap = CacheStorage.HashMap
    private val entryNamer = CacheEntryNamer(namingStrategy)

    override fun invalidate(entryRef: StoredCacheEntryRef<CacheStorage.HashMap>) {
        HashMapStorageStrategy.invalidate(store, entryNamer, entryRef.name, entryRef.cacheArgs, null) {}
    }

    override fun invalidate(partRef: StoredCachePartRef<CacheStorage.HashMap>) {
        HashMapStorageStrategy.invalidate(store, entryNamer, partRef.name, partRef.cacheArgs, partRef.secondaryPatternPartArgs) {}
    }

    override fun invalidate(allRef: StoredCacheAllRef<CacheStorage.HashMap>) {
        HashMapStorageStrategy.invalidateAll(store, entryNamer, allRef)
    }

    override fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.HashMap>,
        returnView: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R = store.invokeAtAddress(
        entryName = HashMapStorageStrategy.storeEntryName(entryNamer.nameEntry(entryRef.name, entryRef.cacheArgs)),
        cacheName = entryRef.name,
        configs = configs,
        codec = returnView.codec,
        saveResultIf = saveResultIf,
        block = block,
    )
}
