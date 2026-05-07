@file:OptIn(com.github.dave08.kacheable.ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.internal.keys

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.StoredPartRef
import com.github.dave08.kacheable.StoredEntryRef
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.joinArgs

@PublishedApi
internal fun validateUniqueKeyPartNames(names: List<String?>) {
    val duplicateName = names
        .filterNotNull()
        .groupingBy { it }
        .eachCount()
        .entries
        .firstOrNull { it.value > 1 }
        ?.key

    require(duplicateName == null) {
        "entryKey key-part name '$duplicateName' is ambiguous. Key-part names must be unique across primary and secondary parts."
    }
}

@PublishedApi
internal data class ResolvedPrimaryKey(
    val primaryPartArgs: List<CacheArgs>,
    val primaryPartNames: List<String?>,
) {
    val cacheArgs: PrimarySecondaryCacheArgs = cacheArgs(
        primaryPartArgs = primaryPartArgs,
        primaryPartNames = primaryPartNames,
    )

    fun stringEntryRef(name: String): StoredCacheEntryRef<CacheStorage.String> = StoredEntryRef(name, cacheArgs, CacheStorage.String)

    fun hashEntryRef(name: String): StoredCacheEntryRef<CacheStorage.HashMap> = StoredEntryRef(name, cacheArgs, CacheStorage.HashMap)

    fun setEntryRef(name: String): StoredCacheEntryRef<CacheStorage.Set> = StoredEntryRef(name, cacheArgs, CacheStorage.Set)

    fun setPartRef(name: String): StoredCachePartRef<CacheStorage.Set> = StoredPartRef(name, cacheArgs.primary, cacheArgs, CacheStorage.Set)
}

@PublishedApi
internal data class ResolvedPrimarySecondaryKey(
    val primaryPartArgs: List<CacheArgs>,
    val primaryPartNames: List<String?>,
    val secondaryPartArgs: List<CacheArgs>,
    val secondaryPartNames: List<String?>,
) {
    val cacheArgs: PrimarySecondaryCacheArgs = cacheArgs(
        primaryPartArgs = primaryPartArgs,
        primaryPartNames = primaryPartNames,
        secondaryPartArgs = secondaryPartArgs,
        secondaryPartNames = secondaryPartNames,
    )

    fun hashEntryRef(name: String): StoredCacheEntryRef<CacheStorage.HashMap> = StoredEntryRef(name, cacheArgs, CacheStorage.HashMap)

    fun setEntryRef(name: String): StoredCacheEntryRef<CacheStorage.Set> = StoredEntryRef(name, cacheArgs, CacheStorage.Set)
}

@PublishedApi
internal fun typedPrimaryKey(
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
): ResolvedPrimaryKey = ResolvedPrimaryKey(
    primaryPartArgs = primaryPartArgs,
    primaryPartNames = primaryPartNames,
)

@PublishedApi
internal fun typedPrimarySecondaryKey(
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
    secondaryPartArgs: List<CacheArgs>,
    secondaryPartNames: List<String?>,
): ResolvedPrimarySecondaryKey = ResolvedPrimarySecondaryKey(
    primaryPartArgs = primaryPartArgs,
    primaryPartNames = primaryPartNames,
    secondaryPartArgs = secondaryPartArgs,
    secondaryPartNames = secondaryPartNames,
)

@PublishedApi
internal fun cacheArgs(
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
    secondaryPartArgs: List<CacheArgs> = emptyList(),
    secondaryPartNames: List<String?> = emptyList(),
): PrimarySecondaryCacheArgs {
    require(primaryPartArgs.size == primaryPartNames.size) {
        "Primary cache arg names must match primary cache arg parts."
    }
    require(secondaryPartArgs.size == secondaryPartNames.size) {
        "Secondary cache arg names must match secondary cache arg parts."
    }

    return PrimarySecondaryCacheArgs(
        primary = joinArgs(*primaryPartArgs.toTypedArray()),
        secondary = secondaryPartArgs.takeIf { it.isNotEmpty() }?.let { joinArgs(*it.toTypedArray()) },
        primaryPartArgs = primaryPartArgs,
        primaryPartNames = primaryPartNames,
        secondaryPartArgs = secondaryPartArgs,
        secondaryPartNames = secondaryPartNames,
    )
}
