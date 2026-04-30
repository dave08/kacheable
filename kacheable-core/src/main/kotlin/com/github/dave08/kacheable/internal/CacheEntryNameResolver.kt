package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.combineSecondaryEntryPatternParts
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
        storage: CacheStorage,
    ): CacheEntryName {
        return when (storage) {
            CacheStorage.String -> namingStrategy.getEntryName(
                cacheName = name,
                storage = CacheStorage.String,
                primaryParams = cacheArgs.primary.toParamsArray(),
                secondaryParams = cacheArgs.secondary?.toParamsArray() ?: emptyArray(),
            )
            CacheStorage.HashMap -> resolveHashValue(name, cacheArgs)
            CacheStorage.Set -> error("Set storage should be handled through the set membership path.")
        }
    }

    private fun resolveHashValue(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
    ): CacheEntryName =
        namingStrategy.getEntryName(
            cacheName = name,
            storage = CacheStorage.HashMap,
            primaryParams = cacheArgs.primary.toParamsArray(),
            secondaryParams = cacheArgs.secondary?.toParamsArray() ?: emptyArray(),
        )

    fun resolvePattern(
        name: String,
        primaryArgs: CacheArgs,
        secondaryPatternPartArgs: List<CacheArgs>,
        storage: CacheStorage,
    ): CacheEntryName {
        require(storage == CacheStorage.HashMap) {
            "Secondary-pattern invalidation is currently only supported for layered hash storage."
        }

        return namingStrategy.getEntryName(
            cacheName = name,
            storage = CacheStorage.HashMap,
            primaryParams = primaryArgs.toParamsArray(),
            secondaryParams = combineSecondaryEntryPatternParts(secondaryPatternPartArgs),
        )
    }
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
internal suspend fun KacheableStore.deleteMatching(entryName: CacheEntryName.Layered) {
    deleteHashValuesMatching(entryName.cacheKey, entryName.requireSecondaryEntry())
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun StoreMutationScope.delete(entryName: CacheEntryName) {
    when (entryName) {
        is CacheEntryName.Flat -> delete(entryName.cacheKey)
        is CacheEntryName.Layered -> deleteHashValue(entryName.cacheKey, entryName.requireSecondaryEntry())
    }
}
