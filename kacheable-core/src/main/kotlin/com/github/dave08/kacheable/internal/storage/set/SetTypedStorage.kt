package com.github.dave08.kacheable.internal.storage.set

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.IsMemberCacheReturn
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.storage.TypedStorage
import com.github.dave08.kacheable.store.KacheableStore

@OptIn(ExperimentalKacheableApi::class)
internal class SetTypedStorage(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val namingStrategy: CacheNamingStrategy,
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
        returnsAs: EnumMemberCacheReturn<E>,
    ) {
        SetStorageStrategy.invalidateClassification(store, namingStrategy, entryRef.name, entryRef.cacheArgs, returnsAs.valueNames) {}
    }

    suspend fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    ) {
        SetStorageStrategy.invalidateClassification(store, namingStrategy, partRef.name, partRef.cacheArgs, returnsAs.valueNames) {}
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = when (returnsAs) {
        is IsMemberCacheReturn -> SetStorageStrategy.invokeMembership(
            store = store,
            configs = configs,
            namingStrategy = namingStrategy,
            name = entryRef.name,
            cacheArgs = entryRef.cacheArgs,
            cacheFalse = returnsAs.cacheFalse,
            saveResultIf = saveResultIf as (Boolean) -> Boolean,
            block = block as suspend () -> Boolean,
        ) as R

        is EnumMemberCacheReturn<*> -> {
            val typedReturn = returnsAs as EnumMemberCacheReturn<Any>
            SetStorageStrategy.invokeClassification(
                store = store,
                configs = configs,
                namingStrategy = namingStrategy,
                name = entryRef.name,
                cacheArgs = entryRef.cacheArgs,
                values = typedReturn.values,
                valueName = typedReturn.valueName,
                saveResultIf = saveResultIf as (Any) -> Boolean,
                block = block as suspend () -> Any,
            ) as R
        }

        else -> error("Set storage does not support return view ${returnsAs::class.simpleName}.")
    }
}
