package com.github.dave08.kacheable.internal.storage.string

import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.combinedKey
import com.github.dave08.kacheable.internal.storage.StoreEntryName
import com.github.dave08.kacheable.internal.storage.delete as blockingDelete
import com.github.dave08.kacheable.internal.storage.delete

@OptIn(ExperimentalKacheableApi::class)
internal object StringStorageStrategy {
    val storage: CacheStorage.String = CacheStorage.String

    fun storeEntryName(entryName: CacheEntryName): StoreEntryName.Flat =
        StoreEntryName.Flat(entryName.combinedKey)

    suspend fun <R> invalidate(
        store: com.github.dave08.kacheable.store.KacheableStore,
        entryNamer: com.github.dave08.kacheable.internal.storage.CacheEntryNamer,
        name: String,
        cacheArgs: com.github.dave08.kacheable.PrimarySecondaryCacheArgs,
        secondaryPatternPartArgs: List<com.github.dave08.kacheable.CacheArgs>?,
        block: suspend () -> R,
    ): R {
        store.mutate {
            delete(storeEntryName(entryNamer.nameEntry(name, cacheArgs)))
        }
        return block()
    }

    fun <R> invalidate(
        store: com.github.dave08.kacheable.blocking.store.BlockingKacheableStore,
        entryNamer: com.github.dave08.kacheable.internal.storage.CacheEntryNamer,
        name: String,
        cacheArgs: com.github.dave08.kacheable.PrimarySecondaryCacheArgs,
        secondaryPatternPartArgs: List<com.github.dave08.kacheable.CacheArgs>?,
        block: () -> R,
    ): R {
        store.mutate {
            blockingDelete(storeEntryName(entryNamer.nameEntry(name, cacheArgs)))
        }
        return block()
    }
}
