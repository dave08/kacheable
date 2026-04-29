package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingStoreMutationScope
import com.github.dave08.kacheable.requireSecondaryEntry

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingKacheableStore.get(entryName: CacheEntryName): String? =
    when (entryName) {
        is CacheEntryName.Flat -> get(entryName.cacheKey)
        is CacheEntryName.Layered -> getHashValue(entryName.cacheKey, entryName.requireSecondaryEntry())
    }

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingKacheableStore.set(entryName: CacheEntryName, value: String) {
    when (entryName) {
        is CacheEntryName.Flat -> set(entryName.cacheKey, value)
        is CacheEntryName.Layered -> setHashValue(entryName.cacheKey, entryName.requireSecondaryEntry(), value)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingStoreMutationScope.set(entryName: CacheEntryName, value: String) {
    when (entryName) {
        is CacheEntryName.Flat -> set(entryName.cacheKey, value)
        is CacheEntryName.Layered -> setHashValue(entryName.cacheKey, entryName.requireSecondaryEntry(), value)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingKacheableStore.delete(entryName: CacheEntryName) {
    when (entryName) {
        is CacheEntryName.Flat -> delete(entryName.cacheKey)
        is CacheEntryName.Layered -> deleteHashValue(entryName.cacheKey, entryName.requireSecondaryEntry())
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingKacheableStore.deleteMatching(entryName: CacheEntryName.Layered) {
    deleteHashValuesMatching(entryName.cacheKey, entryName.requireSecondaryEntry())
}

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingStoreMutationScope.delete(entryName: CacheEntryName) {
    when (entryName) {
        is CacheEntryName.Flat -> delete(entryName.cacheKey)
        is CacheEntryName.Layered -> deleteHashValue(entryName.cacheKey, entryName.requireSecondaryEntry())
    }
}
