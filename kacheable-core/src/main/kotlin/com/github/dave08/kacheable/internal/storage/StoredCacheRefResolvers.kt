@file:OptIn(com.github.dave08.kacheable.ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.StoredEntryRef
import com.github.dave08.kacheable.StoredPartRef
import com.github.dave08.kacheable.CachePatternWildcard
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.KeyPartValue
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.SupportsPrimarySecondaryKeyStorage
import com.github.dave08.kacheable.joinArgs
import com.github.dave08.kacheable.internal.keys.cacheArgs
import com.github.dave08.kacheable.internal.keys.typedPrimaryKey
import com.github.dave08.kacheable.internal.keys.typedPrimarySecondaryKey
import com.github.dave08.kacheable.internal.keys.TypedPrimarySecondaryKeyDefinition

internal fun resolveStoredPrimaryEntryRef(
    name: String,
    storedAs: CacheStorage,
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
): StoredCacheEntryRef<*> = when (storedAs) {
    CacheStorage.String -> typedPrimaryKey(primaryPartArgs, primaryPartNames).stringEntryRef(name)
    CacheStorage.HashMap -> typedPrimaryKey(primaryPartArgs, primaryPartNames).hashEntryRef(name)
    CacheStorage.Set -> typedPrimaryKey(primaryPartArgs, primaryPartNames).setEntryRef(name)
}

internal fun resolveStoredPrimaryPartRef(
    name: String,
    storedAs: CacheStorage,
    args: CacheArgs,
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
) : StoredCachePartRef<*> = when (storedAs) {
    CacheStorage.String -> StoredPartRef(
        name = name,
        args = args,
        cacheArgs = cacheArgs(
            primaryPartArgs = primaryPartArgs,
            primaryPartNames = primaryPartNames,
        ),
        storage = CacheStorage.String,
    )
    CacheStorage.HashMap -> StoredPartRef(
        name = name,
        args = args,
        cacheArgs = cacheArgs(
            primaryPartArgs = primaryPartArgs,
            primaryPartNames = primaryPartNames,
        ),
        storage = CacheStorage.HashMap,
    )
    CacheStorage.Set -> typedPrimaryKey(
        primaryPartArgs = primaryPartArgs,
        primaryPartNames = primaryPartNames,
    ).setPartRef(name)
}

private fun wildcardPartArgs(keyPart: KeyPart<*>): CacheArgs {
    val segmentCount = requireNotNull(keyPart.segmentCount) {
        "Partial invalidation requires fixed-size key parts. rawKeyPart() cannot be used here."
    }
    return com.github.dave08.kacheable.argsOf(*Array(segmentCount) { CachePatternWildcard })
}

private fun buildSecondaryPatternPartArgs(
    secondaryParts: List<KeyPart<*>>,
    selections: Array<out KeyPartValue>,
): List<CacheArgs> {
    if (selections.isEmpty()) return secondaryParts.map(::wildcardPartArgs)

    val selectedArgsByIndex = mutableMapOf<Int, CacheArgs>()
    selections.forEach { selection ->
        val selectionName = requireNotNull(selection.name) {
            "Partial invalidation requires named key parts. Use keyPart<T>(\"name\") or delegated key parts."
        }
        val matches = secondaryParts.withIndex().filter { (_, part) ->
            part.name == selectionName && selection.args.sizeMatches(part.segmentCount)
        }

        require(matches.isNotEmpty()) {
            "Selected key part $selectionName is not part of this entryKey."
        }
        require(matches.size == 1) {
            "Selected key part $selectionName matches more than one secondary key part."
        }

        val index = matches.single().index
        require(selectedArgsByIndex.put(index, selection.args) == null) {
            "Secondary key part $selectionName was selected more than once."
        }
    }

    return secondaryParts.mapIndexed { index, part ->
        selectedArgsByIndex[index] ?: wildcardPartArgs(part)
    }
}

private fun buildHashPrimarySecondarySelectionPartRef(
    name: String,
    primaryParts: List<KeyPart<*>>,
    secondaryParts: List<KeyPart<*>>,
    selections: Array<out KeyPartValue>,
): StoredCachePartRef<CacheStorage.HashMap> {
    require(selections.isNotEmpty()) { "Partial invalidation requires at least one selected key part." }

    val selectionsByName = selections.associateByName()
    val primaryPartArgs = primaryParts.map { primaryPart ->
        val primaryName = requireNotNull(primaryPart.name) {
            "Partial invalidation requires named primary key parts. Use keyPart<T>(\"name\") or delegated key parts."
        }
        requireNotNull(selectionsByName.remove(primaryName)) {
            "Partial hash invalidation requires all primary key parts. Primary wildcard invalidation is not supported."
        }.args.also { partArgs ->
            require(partArgs.sizeMatches(primaryPart.segmentCount)) {
                "Selected key part $primaryName has the wrong number of cache segments."
            }
        }
    }

    val secondarySelections = selectionsByName.map { (selectionName, selection) ->
        require(secondaryParts.any { part -> part.name == selectionName }) {
            "Selected key part $selectionName is not part of this entryKey."
        }
        selection
    }.toTypedArray()

    return StoredPartRef(
        name = name,
        args = joinArgs(*primaryPartArgs.toTypedArray()),
        cacheArgs = cacheArgs(
            primaryPartArgs = primaryPartArgs,
            primaryPartNames = primaryParts.map { it.name },
        ),
        storage = CacheStorage.HashMap,
        secondaryPatternPartArgs = secondarySelections
            .takeIf { it.isNotEmpty() }
            ?.let { buildSecondaryPatternPartArgs(secondaryParts, it) },
    )
}

private fun Array<out KeyPartValue>.associateByName(): MutableMap<String, KeyPartValue> {
    val selectionsByName = mutableMapOf<String, KeyPartValue>()
    forEach { selection ->
        val selectionName = requireNotNull(selection.name) {
            "Partial invalidation requires named key parts. Use keyPart<T>(\"name\") or delegated key parts."
        }
        require(selectionsByName.put(selectionName, selection) == null) {
            "Key part $selectionName was selected more than once."
        }
    }
    return selectionsByName
}

private fun CacheArgs.sizeMatches(expectedSize: Int?): Boolean = expectedSize == null || toParamsArray().size == expectedSize

@Suppress("UNCHECKED_CAST")
internal fun <S> resolveStoredPrimarySecondaryPrimaryEntryRef(
    name: String,
    storedAs: S,
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
): StoredCacheEntryRef<S> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    when (storedAs) {
        CacheStorage.HashMap -> typedPrimaryKey(primaryPartArgs, primaryPartNames).hashEntryRef(name)
        CacheStorage.Set -> StoredEntryRef(
            name = name,
            cacheArgs = cacheArgs(
                primaryPartArgs = primaryPartArgs,
                primaryPartNames = primaryPartNames,
            ),
            storage = CacheStorage.Set,
        )
    } as StoredCacheEntryRef<S>

@Suppress("UNCHECKED_CAST")
internal fun <S> resolveStoredPrimarySecondaryEntryRef(
    name: String,
    storedAs: S,
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
    secondaryPartArgs: List<CacheArgs>,
    secondaryPartNames: List<String?>,
): StoredCacheEntryRef<S> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    when (storedAs) {
        CacheStorage.HashMap -> typedPrimarySecondaryKey(
            primaryPartArgs = primaryPartArgs,
            primaryPartNames = primaryPartNames,
            secondaryPartArgs = secondaryPartArgs,
            secondaryPartNames = secondaryPartNames,
        ).hashEntryRef(name)
        CacheStorage.Set -> typedPrimarySecondaryKey(
            primaryPartArgs = primaryPartArgs,
            primaryPartNames = primaryPartNames,
            secondaryPartArgs = secondaryPartArgs,
            secondaryPartNames = secondaryPartNames,
        ).setEntryRef(name)
    } as StoredCacheEntryRef<S>

@Suppress("UNCHECKED_CAST")
internal fun <P1 : Any, S> resolveStoredPrimarySecondaryPrimaryPartRef(
    name: String,
    storedAs: S,
    key: TypedPrimarySecondaryKeyDefinition<P1>,
    primaryValue: P1,
): StoredCachePartRef<S> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    when (storedAs) {
        CacheStorage.HashMap -> StoredPartRef(
            name = name,
            args = key.primary.encode(primaryValue),
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(primaryValue),
                primaryPartNames = key.primaryPartNames(),
            ),
            storage = CacheStorage.HashMap,
        )
        CacheStorage.Set -> StoredPartRef(
            name = name,
            args = key.primary.encode(primaryValue),
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(primaryValue),
                primaryPartNames = key.primaryPartNames(),
            ),
            storage = CacheStorage.Set,
        )
    } as StoredCachePartRef<S>

internal fun <P1 : Any> resolveHashPrimarySecondaryPrimaryPartRef(
    name: String,
    key: TypedPrimarySecondaryKeyDefinition<P1>,
    primaryValue: P1,
    secondarySelections: Array<out KeyPartValue>,
): StoredCachePartRef<CacheStorage.HashMap> = StoredPartRef(
    name = name,
    args = key.primary.encode(primaryValue),
    cacheArgs = cacheArgs(
        primaryPartArgs = key.encodePrimaryParts(primaryValue),
        primaryPartNames = key.primaryPartNames(),
    ),
    storage = CacheStorage.HashMap,
    secondaryPatternPartArgs = secondarySelections
        .takeIf { it.isNotEmpty() }
        ?.let { buildSecondaryPatternPartArgs(key.secondaryParts(), it) },
)

internal fun <P1 : Any> resolveHashPrimarySecondarySelectedPartRef(
    name: String,
    key: TypedPrimarySecondaryKeyDefinition<P1>,
    selections: Array<out KeyPartValue>,
): StoredCachePartRef<CacheStorage.HashMap> =
    buildHashPrimarySecondarySelectionPartRef(name, listOf(key.primary), key.secondaryParts(), selections)
