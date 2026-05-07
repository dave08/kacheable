package com.github.dave08.kacheable.internal.storage.hash

import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.storage.StoreEntryName
import com.github.dave08.kacheable.primaryKey
import com.github.dave08.kacheable.internal.storage.delete as blockingDelete
import com.github.dave08.kacheable.internal.storage.delete
import com.github.dave08.kacheable.internal.storage.deleteMatching

@OptIn(ExperimentalKacheableApi::class)
internal object HashMapStorageStrategy {
    val storage: CacheStorage.HashMap = CacheStorage.HashMap

    fun storeEntryName(entryName: CacheEntryName): StoreEntryName =
        when (entryName) {
            is CacheEntryName.Primary -> StoreEntryName.Flat(entryName.primary)
            is CacheEntryName.PrimarySecondary -> StoreEntryName.Layered(entryName.primary, entryName.secondary)
        }

    fun patternEntryName(entryName: CacheEntryName.PrimarySecondary): StoreEntryName.Layered =
        StoreEntryName.Layered(entryName.primary, entryName.secondary)

    suspend fun invalidateAll(
        store: com.github.dave08.kacheable.store.KacheableStore,
        entryNamer: CacheEntryNamer,
        allRef: com.github.dave08.kacheable.StoredCacheAllRef<CacheStorage.HashMap>,
    ) {
        val entryName = entryNamer.nameAllEntries(allRef.name)
        store.delete(entryName.primaryKey)
    }

    fun invalidateAll(
        store: com.github.dave08.kacheable.blocking.store.BlockingKacheableStore,
        entryNamer: CacheEntryNamer,
        allRef: com.github.dave08.kacheable.StoredCacheAllRef<CacheStorage.HashMap>,
    ) {
        val entryName = entryNamer.nameAllEntries(allRef.name)
        store.delete(entryName.primaryKey)
    }

    suspend fun <R> invalidate(
        store: com.github.dave08.kacheable.store.KacheableStore,
        entryNamer: CacheEntryNamer,
        name: String,
        cacheArgs: com.github.dave08.kacheable.PrimarySecondaryCacheArgs,
        secondaryPatternPartArgs: List<com.github.dave08.kacheable.CacheArgs>?,
        block: suspend () -> R,
    ): R {
        if (secondaryPatternPartArgs != null) {
            store.deleteMatching(
                patternEntryName(entryNamer.namePatternEntry(name, cacheArgs.primary, secondaryPatternPartArgs)),
            )
        } else {
            store.mutate {
                delete(storeEntryName(entryNamer.nameEntry(name, cacheArgs)))
            }
        }
        return block()
    }

    fun <R> invalidate(
        store: com.github.dave08.kacheable.blocking.store.BlockingKacheableStore,
        entryNamer: CacheEntryNamer,
        name: String,
        cacheArgs: com.github.dave08.kacheable.PrimarySecondaryCacheArgs,
        secondaryPatternPartArgs: List<com.github.dave08.kacheable.CacheArgs>?,
        block: () -> R,
    ): R {
        if (secondaryPatternPartArgs != null) {
            val entryName =
                patternEntryName(entryNamer.namePatternEntry(name, cacheArgs.primary, secondaryPatternPartArgs))
            store.deleteHashValuesMatching(entryName.key, entryName.entry)
        } else {
            store.mutate {
                blockingDelete(storeEntryName(entryNamer.nameEntry(name, cacheArgs)))
            }
        }
        return block()
    }
}
