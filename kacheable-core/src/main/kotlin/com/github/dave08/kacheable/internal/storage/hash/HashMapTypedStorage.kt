package com.github.dave08.kacheable.internal.storage.hash

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.snapshot.CacheSnapshotCoordinator
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.storage.TypedStorage
import com.github.dave08.kacheable.internal.storage.invokeAtAddress
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CoroutineScope

internal class HashMapTypedStorage(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val loadCoordinator: CacheLoadCoordinator,
    namingStrategy: CacheNamingStrategy,
    private val snapshotCoordinator: CacheSnapshotCoordinator?,
    private val backgroundScope: () -> CoroutineScope,
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

    override suspend fun invalidate(allRef: StoredCacheAllRef<CacheStorage.HashMap>) {
        HashMapStorageStrategy.invalidateAll(store, entryNamer, allRef)
    }

    override suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.HashMap>,
        returnView: CacheReturn<R, *>,
        missPolicy: CacheMissPolicy<R>,
        refreshPolicy: CacheRefreshPolicy<R>,
        storeResultIf: (R) -> Boolean,
        block: suspend (previous: R?) -> R,
    ): R = store.invokeAtAddress(
        entryName = HashMapStorageStrategy.storeEntryName(entryNamer.nameEntry(entryRef.name, entryRef.cacheArgs)),
        cacheName = entryRef.name,
        configs = configs,
        loadCoordinator = loadCoordinator,
        snapshotCoordinator = snapshotCoordinator,
        backgroundScope = backgroundScope,
        codec = returnView.codec,
        missPolicy = missPolicy,
        refreshPolicy = refreshPolicy,
        storeResultIf = storeResultIf,
        block = block,
    )
}
