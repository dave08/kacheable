package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.storage.hash.HashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.set.SetTypedStorage
import com.github.dave08.kacheable.internal.storage.string.StringTypedStorage

@OptIn(ExperimentalKacheableApi::class)
internal interface TypedStorage<S : CacheStorage> {
    val storage: S

    suspend fun invalidate(entryRef: StoredCacheEntryRef<S>)

    suspend fun invalidate(partRef: StoredCachePartRef<S>)

    suspend fun invalidate(allRef: StoredCacheAllRef<S>)

    suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R
}

@OptIn(ExperimentalKacheableApi::class)
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
