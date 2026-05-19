package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.storage.hash.HashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.set.SetTypedStorage
import com.github.dave08.kacheable.internal.storage.string.StringTypedStorage

internal interface TypedStorage<S : CacheStorage> {
    val storage: S

    suspend fun invalidate(entryRef: StoredCacheEntryRef<S>)

    suspend fun invalidate(partRef: StoredCachePartRef<S>)

    suspend fun invalidate(allRef: StoredCacheAllRef<S>)

    suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = invoke(
        entryRef = entryRef,
        returnView = returnView,
        missPolicy = CacheMissPolicy.load(),
        refreshPolicy = CacheRefreshPolicy.neverRefresh(),
        storeResultIf = saveResultIf,
    ) { block() }

    suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        missPolicy: CacheMissPolicy<R>,
        block: suspend () -> R,
    ): R = invoke(
        entryRef = entryRef,
        returnView = returnView,
        missPolicy = missPolicy,
        refreshPolicy = CacheRefreshPolicy.neverRefresh(),
        storeResultIf = { true },
    ) { block() }

    suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        missPolicy: CacheMissPolicy<R>,
        refreshPolicy: CacheRefreshPolicy<R>,
        storeResultIf: (R) -> Boolean,
        block: suspend (previous: R?) -> R,
    ): R
}

internal data class TypedStorages(
    val string: StringTypedStorage,
    val hashMap: HashMapTypedStorage,
    val set: SetTypedStorage,
) {
    fun any(storage: CacheStorage): TypedStorage<out CacheStorage> = when (storage) {
        CacheStorage.String -> string
        CacheStorage.HashMap -> hashMap
        CacheStorage.Set -> set
    }
}
