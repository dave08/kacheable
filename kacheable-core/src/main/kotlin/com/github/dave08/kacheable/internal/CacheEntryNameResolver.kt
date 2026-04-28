package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheKeyGroups
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheStorageLayout
import com.github.dave08.kacheable.CacheWildcard
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.asCombined
import com.github.dave08.kacheable.baseKey
import com.github.dave08.kacheable.requireEntry
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.StoreMutationScope

@OptIn(ExperimentalKacheableApi::class)
internal class CacheEntryNameResolver(
    private val namingStrategy: CacheNamingStrategy,
) {
    fun resolve(name: String, params: Array<out Any>): CacheEntryName =
        namingStrategy.getEntryName(name, CacheStorage.String, params, emptyArray())

    fun resolve(
        name: String,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
    ): CacheEntryName {
        return when (storageLayout) {
            CacheStorageLayout.StringValue -> namingStrategy.getEntryName(
                cacheName = name,
                storage = CacheStorage.String,
                mainParams = keyGroups.main.toParamsArray(),
                secondaryParams = keyGroups.secondary?.toParamsArray() ?: emptyArray(),
            )
            CacheStorageLayout.HashValue -> resolveHashValue(name, keyGroups)
        }
    }

    private fun resolveHashValue(
        name: String,
        keyGroups: CacheKeyGroups,
    ): CacheEntryName {
        val secondary = keyGroups.secondary
        val resolved = namingStrategy.getEntryName(
            cacheName = name,
            storage = CacheStorage.HashMap,
            mainParams = keyGroups.main.toParamsArray(),
            secondaryParams = secondary?.toParamsArray() ?: emptyArray(),
        )

        return if (secondary?.isWildcard() == true) {
            resolved.asCombined()
        } else {
            resolved
        }
    }

    private fun CacheArgs.isWildcard(): Boolean =
        toParamsArray().all { it == CacheWildcard }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.get(entryName: CacheEntryName): String? =
    when (entryName) {
        is CacheEntryName.Combined -> get(entryName.baseKey)
        is CacheEntryName.Split -> getHashValue(entryName.baseKey, entryName.requireEntry())
    }

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.set(entryName: CacheEntryName, value: String) {
    when (entryName) {
        is CacheEntryName.Combined -> set(entryName.baseKey, value)
        is CacheEntryName.Split -> setHashValue(entryName.baseKey, entryName.requireEntry(), value)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun StoreMutationScope.set(entryName: CacheEntryName, value: String) {
    when (entryName) {
        is CacheEntryName.Combined -> set(entryName.baseKey, value)
        is CacheEntryName.Split -> setHashValue(entryName.baseKey, entryName.requireEntry(), value)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.delete(entryName: CacheEntryName) {
    when (entryName) {
        is CacheEntryName.Combined -> delete(entryName.baseKey)
        is CacheEntryName.Split -> deleteHashValue(entryName.baseKey, entryName.requireEntry())
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun StoreMutationScope.delete(entryName: CacheEntryName) {
    when (entryName) {
        is CacheEntryName.Combined -> delete(entryName.baseKey)
        is CacheEntryName.Split -> deleteHashValue(entryName.baseKey, entryName.requireEntry())
    }
}
