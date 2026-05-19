package com.github.dave08.kacheable.internal.storage.set

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.IsMemberCacheReturn
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.storage.TypedStorage
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CoroutineScope

internal class SetTypedStorage(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val loadCoordinator: CacheLoadCoordinator,
    private val namingStrategy: CacheNamingStrategy,
    private val backgroundScope: () -> CoroutineScope,
) : TypedStorage<CacheStorage.Set> {
    override val storage: CacheStorage.Set = CacheStorage.Set
    private val entryNamer = com.github.dave08.kacheable.internal.storage.CacheEntryNamer(namingStrategy)

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<CacheStorage.Set>) {
        SetStorageStrategy.invalidateMembership(store, namingStrategy, entryRef.name, entryRef.cacheArgs) {}
    }

    override suspend fun invalidate(partRef: StoredCachePartRef<CacheStorage.Set>) {
        SetStorageStrategy.invalidateMembership(store, namingStrategy, partRef.name, partRef.cacheArgs) {}
    }

    override suspend fun invalidate(allRef: StoredCacheAllRef<CacheStorage.Set>) {
        SetStorageStrategy.invalidateAll(store, entryNamer, allRef)
    }

    suspend fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        SetStorageStrategy.invalidateClassification(store, namingStrategy, entryRef.name, entryRef.cacheArgs, returnView.valueNames) {}
    }

    suspend fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        SetStorageStrategy.invalidateClassification(store, namingStrategy, partRef.name, partRef.cacheArgs, returnView.valueNames) {}
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: CacheReturn<R, *>,
        missPolicy: CacheMissPolicy<R>,
        refreshPolicy: CacheRefreshPolicy<R>,
        storeResultIf: (R) -> Boolean,
        block: suspend (previous: R?) -> R,
    ): R = when (returnView) {
        is IsMemberCacheReturn -> SetStorageStrategy.invokeMembership(
            store = store,
            configs = configs,
            namingStrategy = namingStrategy,
            name = entryRef.name,
            cacheArgs = entryRef.cacheArgs,
            cacheFalse = returnView.cacheFalse,
            missPolicy = missPolicy as CacheMissPolicy<Boolean>,
            refreshPolicy = refreshPolicy as CacheRefreshPolicy<Boolean>,
            storeResultIf = storeResultIf as (Boolean) -> Boolean,
            loadCoordinator = loadCoordinator,
            backgroundScope = backgroundScope,
            block = block as suspend (Boolean?) -> Boolean,
        ) as R

        is EnumMemberCacheReturn<*> -> {
            val typedReturn = returnView as EnumMemberCacheReturn<Any>
            SetStorageStrategy.invokeClassification(
                store = store,
                configs = configs,
                namingStrategy = namingStrategy,
                name = entryRef.name,
                cacheArgs = entryRef.cacheArgs,
                values = typedReturn.values,
                valueName = typedReturn.valueName,
                missPolicy = missPolicy as CacheMissPolicy<Any>,
                refreshPolicy = refreshPolicy as CacheRefreshPolicy<Any>,
                storeResultIf = storeResultIf as (Any) -> Boolean,
                loadCoordinator = loadCoordinator,
                backgroundScope = backgroundScope,
                block = block as suspend (Any?) -> Any,
            ) as R
        }

        else -> error("Set storage does not support return view ${returnView::class.simpleName}.")
    }
}
