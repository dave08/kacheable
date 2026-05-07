package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.storage.hash.BlockingHashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.set.BlockingSetTypedStorage
import com.github.dave08.kacheable.internal.storage.string.BlockingStringTypedStorage

@OptIn(ExperimentalKacheableApi::class)
internal interface BlockingTypedStorage<S : CacheStorage> {
    val storage: S

    fun invalidate(entryRef: StoredCacheEntryRef<S>)

    fun invalidate(partRef: StoredCachePartRef<S>)

    fun invalidate(allRef: StoredCacheAllRef<S>)

    fun <R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R
}

@OptIn(ExperimentalKacheableApi::class)
internal data class BlockingTypedStorages(
    val string: BlockingStringTypedStorage,
    val hashMap: BlockingHashMapTypedStorage,
    val set: BlockingSetTypedStorage,
) {
    fun any(storage: CacheStorage): BlockingTypedStorage<out CacheStorage> = when (storage) {
        CacheStorage.String -> string
        CacheStorage.HashMap -> hashMap
        CacheStorage.Set -> set
    }
}
