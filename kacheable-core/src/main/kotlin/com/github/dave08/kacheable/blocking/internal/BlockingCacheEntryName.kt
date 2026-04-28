package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.baseKey
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingStoreMutationScope
import com.github.dave08.kacheable.requireEntry

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingKacheableStore.get(entryName: CacheEntryName): String? =
    when (entryName) {
        is CacheEntryName.Combined -> get(entryName.baseKey)
        is CacheEntryName.Split -> getHashValue(entryName.baseKey, entryName.requireEntry())
    }

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingKacheableStore.set(entryName: CacheEntryName, value: String) {
    when (entryName) {
        is CacheEntryName.Combined -> set(entryName.baseKey, value)
        is CacheEntryName.Split -> setHashValue(entryName.baseKey, entryName.requireEntry(), value)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingStoreMutationScope.set(entryName: CacheEntryName, value: String) {
    when (entryName) {
        is CacheEntryName.Combined -> set(entryName.baseKey, value)
        is CacheEntryName.Split -> setHashValue(entryName.baseKey, entryName.requireEntry(), value)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingKacheableStore.delete(entryName: CacheEntryName) {
    when (entryName) {
        is CacheEntryName.Combined -> delete(entryName.baseKey)
        is CacheEntryName.Split -> deleteHashValue(entryName.baseKey, entryName.requireEntry())
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingStoreMutationScope.delete(entryName: CacheEntryName) {
    when (entryName) {
        is CacheEntryName.Combined -> delete(entryName.baseKey)
        is CacheEntryName.Split -> deleteHashValue(entryName.baseKey, entryName.requireEntry())
    }
}
