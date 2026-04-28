package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheStorageLayout
import com.github.dave08.kacheable.CacheWildcard
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.asFlat
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.requireSecondaryEntry
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
        cacheArgs: PrimarySecondaryCacheArgs,
        storageLayout: CacheStorageLayout,
    ): CacheEntryName {
        return when (storageLayout) {
            CacheStorageLayout.StringValue -> namingStrategy.getEntryName(
                cacheName = name,
                storage = CacheStorage.String,
                primaryParams = cacheArgs.primary.toParamsArray(),
                secondaryParams = cacheArgs.secondary?.toParamsArray() ?: emptyArray(),
            )
            CacheStorageLayout.HashValue -> resolveHashValue(name, cacheArgs)
        }
    }

    private fun resolveHashValue(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
    ): CacheEntryName {
        val secondary = cacheArgs.secondary
        val resolved = namingStrategy.getEntryName(
            cacheName = name,
            storage = CacheStorage.HashMap,
            primaryParams = cacheArgs.primary.toParamsArray(),
            secondaryParams = secondary?.toParamsArray() ?: emptyArray(),
        )

        return if (secondary?.isWildcard() == true) {
            resolved.asFlat()
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
        is CacheEntryName.Flat -> get(entryName.cacheKey)
        is CacheEntryName.Layered -> getHashValue(entryName.cacheKey, entryName.requireSecondaryEntry())
    }

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.set(entryName: CacheEntryName, value: String) {
    when (entryName) {
        is CacheEntryName.Flat -> set(entryName.cacheKey, value)
        is CacheEntryName.Layered -> setHashValue(entryName.cacheKey, entryName.requireSecondaryEntry(), value)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun StoreMutationScope.set(entryName: CacheEntryName, value: String) {
    when (entryName) {
        is CacheEntryName.Flat -> set(entryName.cacheKey, value)
        is CacheEntryName.Layered -> setHashValue(entryName.cacheKey, entryName.requireSecondaryEntry(), value)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.delete(entryName: CacheEntryName) {
    when (entryName) {
        is CacheEntryName.Flat -> delete(entryName.cacheKey)
        is CacheEntryName.Layered -> deleteHashValue(entryName.cacheKey, entryName.requireSecondaryEntry())
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun StoreMutationScope.delete(entryName: CacheEntryName) {
    when (entryName) {
        is CacheEntryName.Flat -> delete(entryName.cacheKey)
        is CacheEntryName.Layered -> deleteHashValue(entryName.cacheKey, entryName.requireSecondaryEntry())
    }
}
