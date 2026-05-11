@file:OptIn(com.github.dave08.kacheable.ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.internal.keys

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
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
        "Cache key-part name '$duplicateName' is ambiguous. Key-part names must be unique within a cache key."
    }
}

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
