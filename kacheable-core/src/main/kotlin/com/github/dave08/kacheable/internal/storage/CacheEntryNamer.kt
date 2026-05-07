package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.StoreMutationScope

internal sealed interface StoreEntryName {
    data class Flat(val key: String) : StoreEntryName

    data class Layered(val key: String, val entry: String) : StoreEntryName
}

@OptIn(ExperimentalKacheableApi::class)
internal class CacheEntryNamer(
    private val namingStrategy: CacheNamingStrategy,
) {
    fun nameEntry(name: String, params: Array<out Any?>): CacheEntryName =
        namingStrategy.getEntryName(name, params, emptyArray())

    fun nameEntry(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
    ): CacheEntryName = namingStrategy.getEntryName(
        cacheName = name,
        primaryParams = cacheArgs.primary.toParamsArray(),
        secondaryParams = cacheArgs.secondary?.toParamsArray() ?: emptyArray(),
    )

    fun namePatternEntry(
        name: String,
        primaryArgs: CacheArgs,
        secondaryPatternPartArgs: List<CacheArgs>,
    ): CacheEntryName.PrimarySecondary =
        namingStrategy.getEntryName(
            cacheName = name,
            primaryParams = primaryArgs.toParamsArray(),
            secondaryParams = secondaryPatternPartArgs.flatMap { it.toParamsArray().asList() }.toTypedArray(),
        ) as CacheEntryName.PrimarySecondary
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.get(entryName: StoreEntryName): String? =
    when (entryName) {
        is StoreEntryName.Flat -> get(entryName.key)
        is StoreEntryName.Layered -> getHashValue(entryName.key, entryName.entry)
    }

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.set(entryName: StoreEntryName, value: String) {
    when (entryName) {
        is StoreEntryName.Flat -> set(entryName.key, value)
        is StoreEntryName.Layered -> setHashValue(entryName.key, entryName.entry, value)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun StoreMutationScope.set(entryName: StoreEntryName, value: String) {
    when (entryName) {
        is StoreEntryName.Flat -> set(entryName.key, value)
        is StoreEntryName.Layered -> setHashValue(entryName.key, entryName.entry, value)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.delete(entryName: StoreEntryName) {
    when (entryName) {
        is StoreEntryName.Flat -> delete(entryName.key)
        is StoreEntryName.Layered -> deleteHashValue(entryName.key, entryName.entry)
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.deleteMatching(entryName: StoreEntryName.Layered) {
    deleteHashValuesMatching(entryName.key, entryName.entry)
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun StoreMutationScope.delete(entryName: StoreEntryName) {
    when (entryName) {
        is StoreEntryName.Flat -> delete(entryName.key)
        is StoreEntryName.Layered -> deleteHashValue(entryName.key, entryName.entry)
    }
}
