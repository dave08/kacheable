package com.github.dave08.kacheable.internal.storage.set

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.IsMemberCacheReturn
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorage
import com.github.dave08.kacheable.internal.CacheTelemetryRuntime
import com.github.dave08.kacheable.internal.BlockingLoadConcurrencyCoordinator

internal class BlockingSetTypedStorage(
    private val store: BlockingKacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val namingStrategy: CacheNamingStrategy,
    private val loadCoordinator: BlockingLoadConcurrencyCoordinator,
    private val telemetryRuntime: CacheTelemetryRuntime,
) : BlockingTypedStorage<CacheStorage.Set> {
    override val storage: CacheStorage.Set = CacheStorage.Set
    private val entryNamer = com.github.dave08.kacheable.internal.storage.CacheEntryNamer(namingStrategy)

    override fun invalidate(entryRef: StoredCacheEntryRef<CacheStorage.Set>) {
        SetStorageStrategy.invalidateMembership(store, namingStrategy, entryRef.name, entryRef.cacheArgs) {}
    }

    override fun invalidate(partRef: StoredCachePartRef<CacheStorage.Set>) {
        SetStorageStrategy.invalidateMembership(store, namingStrategy, partRef.name, partRef.cacheArgs) {}
    }

    override fun invalidate(allRef: StoredCacheAllRef<CacheStorage.Set>) {
        SetStorageStrategy.invalidateAll(store, entryNamer, allRef)
    }

    fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        SetStorageStrategy.invalidateClassification(store, namingStrategy, entryRef.name, entryRef.cacheArgs, returnView.valueNames) {}
    }

    fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        SetStorageStrategy.invalidateClassification(store, namingStrategy, partRef.name, partRef.cacheArgs, returnView.valueNames) {}
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R = telemetryRuntime.observeBlocking(
        entryRef.name,
        com.github.dave08.kacheable.CacheStorageKind.Set,
        entryRef.loadConcurrency,
    ) { observation -> when (returnView) {
        is IsMemberCacheReturn -> SetStorageStrategy.invokeMembership(
            store = store,
            configs = configs,
            namingStrategy = namingStrategy,
            name = entryRef.name,
            cacheArgs = entryRef.cacheArgs,
            cacheFalse = returnView.cacheFalse,
            saveResultIf = saveResultIf as (Boolean) -> Boolean,
            loadCoordinator = loadCoordinator,
            loadConcurrency = entryRef.loadConcurrency,
            observation = observation,
            block = block as () -> Boolean,
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
                saveResultIf = saveResultIf as (Any) -> Boolean,
                loadCoordinator = loadCoordinator,
                loadConcurrency = entryRef.loadConcurrency,
                observation = observation,
                block = block as () -> Any,
            ) as R
        }

        else -> error("Set storage does not support return view ${returnView::class.simpleName}.")
    } }
}
