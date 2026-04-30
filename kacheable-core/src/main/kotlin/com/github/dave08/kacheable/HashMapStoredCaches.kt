@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

private fun wildcardPartArgs(keyPart: KeyPart<*>): CacheArgs {
    val segmentCount = requireNotNull(keyPart.segmentCount) {
        "Partial invalidation requires fixed-size key parts. rawKeyPart() cannot be used here."
    }
    return argsOf(*Array(segmentCount) { CachePatternWildcard })
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

private fun buildLayeredHashPartRef(
    name: String,
    primaryParts: List<KeyPart<*>>,
    secondaryParts: List<KeyPart<*>>,
    selections: Array<out KeyPartValue>,
): CachePartRef<CacheStorage.HashMap> {
    require(selections.isNotEmpty()) { "Partial invalidation requires at least one selected key part." }

    val selectionsByName = selections.associateByName()
    val primaryPartArgs = primaryParts.map { primaryPart ->
        val primaryName = requireNotNull(primaryPart.name) {
            "Partial invalidation requires named primary key parts. Use keyPart<T>(\"name\") or delegated key parts."
        }
        requireNotNull(selectionsByName.remove(primaryName)) {
            "Partial hash invalidation requires all primary key parts. Primary wildcard invalidation is not supported."
        }.args.also { args ->
            require(args.sizeMatches(primaryPart.segmentCount)) {
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

    return CachePartRef(
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

private fun typedHashPrimaryKey(
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
): ResolvedPrimaryKey = typedPrimaryKey(
    primaryPartArgs = primaryPartArgs,
    primaryPartNames = primaryPartNames,
)

private fun typedHashPrimarySecondaryKey(
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
    secondaryPartArgs: List<CacheArgs>,
    secondaryPartNames: List<String?>,
): ResolvedPrimarySecondaryKey = typedPrimarySecondaryKey(
    primaryPartArgs = primaryPartArgs,
    primaryPartNames = primaryPartNames,
    secondaryPartArgs = secondaryPartArgs,
    secondaryPartNames = secondaryPartNames,
)

private fun groupedHashEntryRef(
    name: String,
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
): CacheEntryRef<CacheStorage.HashMap> = typedHashPrimaryKey(
    primaryPartArgs = primaryPartArgs,
    primaryPartNames = primaryPartNames,
).hashEntryRef(name)

private fun <P1 : Any> groupedHashEntryRef(
    name: String,
    key: TypedPrimarySecondaryKeyDefinition<P1>,
    primaryValue: P1,
): CacheEntryRef<CacheStorage.HashMap> = typedHashPrimaryKey(
    primaryPartArgs = key.encodePrimaryParts(primaryValue),
    primaryPartNames = key.primaryPartNames(),
).hashEntryRef(name)

private fun <P1 : Any> groupedSetEntryRef(
    name: String,
    key: TypedPrimarySecondaryKeyDefinition<P1>,
    primaryValue: P1,
): CacheEntryRef<CacheStorage.Set> = typedPrimaryKey(
    primaryPartArgs = key.encodePrimaryParts(primaryValue),
    primaryPartNames = key.primaryPartNames(),
).setEntryRef(name)

private fun <P1 : Any> groupedHashPrimaryPartRef(
    name: String,
    key: TypedPrimarySecondaryKeyDefinition<P1>,
    primaryValue: P1,
    secondaryPatternPartArgs: List<CacheArgs>? = null,
): CachePartRef<CacheStorage.HashMap> = CachePartRef(
    name = name,
    args = key.primary.encode(primaryValue),
    cacheArgs = cacheArgs(
        primaryPartArgs = key.encodePrimaryParts(primaryValue),
        primaryPartNames = key.primaryPartNames(),
    ),
    storage = CacheStorage.HashMap,
    secondaryPatternPartArgs = secondaryPatternPartArgs,
)

private fun <P1 : Any> groupedSetPrimaryPartRef(
    name: String,
    key: TypedPrimarySecondaryKeyDefinition<P1>,
    primaryValue: P1,
): CachePartRef<CacheStorage.Set> = CachePartRef(
    name = name,
    args = key.primary.encode(primaryValue),
    cacheArgs = cacheArgs(
        primaryPartArgs = key.encodePrimaryParts(primaryValue),
        primaryPartNames = key.primaryPartNames(),
    ),
    storage = CacheStorage.Set,
)

private fun <P1 : Any> selectedGroupedHashPartRef(
    name: String,
    key: TypedPrimarySecondaryKeyDefinition<P1>,
    selections: Array<out KeyPartValue>,
): CachePartRef<CacheStorage.HashMap> {
    return buildLayeredHashPartRef(name, listOf(key.primary), key.secondaryParts(), selections)
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

@ExperimentalKacheableApi
data class TypedPrimarySecondaryKey2<P1 : Any, P2 : Any, S>(
    val name: String,
    val key: KeyPartCompositionGroup2<P1, P2>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage {

    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1): StoredCacheEntryRef<S> = when (storedAs) {
        CacheStorage.HashMap -> groupedHashEntryRef(name, key, p1)
        CacheStorage.Set -> groupedSetEntryRef(name, key, p1)
    } as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2): StoredCacheEntryRef<S> = when (storedAs) {
        CacheStorage.HashMap -> typedHashPrimarySecondaryKey(
            primaryPartArgs = key.encodePrimaryParts(p1),
            primaryPartNames = key.primaryPartNames(),
            secondaryPartArgs = key.encodeSecondaryParts(p2),
            secondaryPartNames = key.secondaryPartNames(),
        ).hashEntryRef(name)
        CacheStorage.Set -> typedPrimarySecondaryKey(
            primaryPartArgs = key.encodePrimaryParts(p1),
            primaryPartNames = key.primaryPartNames(),
            secondaryPartArgs = key.encodeSecondaryParts(p2),
            secondaryPartNames = key.secondaryPartNames(),
        ).setEntryRef(name)
    } as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(value: P1): StoredCachePartRef<S> = when (storedAs) {
        CacheStorage.HashMap -> groupedHashPrimaryPartRef(name, key, value)
        CacheStorage.Set -> groupedSetPrimaryPartRef(name, key, value)
    } as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimarySecondaryKey3<P1 : Any, P2 : Any, P3 : Any, S>(
    val name: String,
    val key: KeyPartCompositionGroup3<P1, P2, P3>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage {

    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1): StoredCacheEntryRef<S> = when (storedAs) {
        CacheStorage.HashMap -> groupedHashEntryRef(name, key, p1)
        CacheStorage.Set -> groupedSetEntryRef(name, key, p1)
    } as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3): StoredCacheEntryRef<S> = when (storedAs) {
        CacheStorage.HashMap -> typedHashPrimarySecondaryKey(
            primaryPartArgs = key.encodePrimaryParts(p1),
            primaryPartNames = key.primaryPartNames(),
            secondaryPartArgs = key.encodeSecondaryParts(p2, p3),
            secondaryPartNames = key.secondaryPartNames(),
        ).hashEntryRef(name)
        CacheStorage.Set -> typedPrimarySecondaryKey(
            primaryPartArgs = key.encodePrimaryParts(p1),
            primaryPartNames = key.primaryPartNames(),
            secondaryPartArgs = key.encodeSecondaryParts(p2, p3),
            secondaryPartNames = key.secondaryPartNames(),
        ).setEntryRef(name)
    } as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(value: P1): StoredCachePartRef<S> = when (storedAs) {
        CacheStorage.HashMap -> groupedHashPrimaryPartRef(name, key, value)
        CacheStorage.Set -> groupedSetPrimaryPartRef(name, key, value)
    } as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimarySecondaryKey4<P1 : Any, P2 : Any, P3 : Any, P4 : Any, S>(
    val name: String,
    val key: KeyPartCompositionGroup4<P1, P2, P3, P4>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage {

    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1): StoredCacheEntryRef<S> = when (storedAs) {
        CacheStorage.HashMap -> groupedHashEntryRef(name, key, p1)
        CacheStorage.Set -> groupedSetEntryRef(name, key, p1)
    } as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3, p4: P4): StoredCacheEntryRef<S> = when (storedAs) {
        CacheStorage.HashMap -> typedHashPrimarySecondaryKey(
            primaryPartArgs = key.encodePrimaryParts(p1),
            primaryPartNames = key.primaryPartNames(),
            secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4),
            secondaryPartNames = key.secondaryPartNames(),
        ).hashEntryRef(name)
        CacheStorage.Set -> typedPrimarySecondaryKey(
            primaryPartArgs = key.encodePrimaryParts(p1),
            primaryPartNames = key.primaryPartNames(),
            secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4),
            secondaryPartNames = key.secondaryPartNames(),
        ).setEntryRef(name)
    } as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(value: P1): StoredCachePartRef<S> = when (storedAs) {
        CacheStorage.HashMap -> groupedHashPrimaryPartRef(name, key, value)
        CacheStorage.Set -> groupedSetPrimaryPartRef(name, key, value)
    } as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimarySecondaryKey5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S>(
    val name: String,
    val key: KeyPartCompositionGroup5<P1, P2, P3, P4, P5>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage {

    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1): StoredCacheEntryRef<S> = when (storedAs) {
        CacheStorage.HashMap -> groupedHashEntryRef(name, key, p1)
        CacheStorage.Set -> groupedSetEntryRef(name, key, p1)
    } as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): StoredCacheEntryRef<S> = when (storedAs) {
        CacheStorage.HashMap -> typedHashPrimarySecondaryKey(
            primaryPartArgs = key.encodePrimaryParts(p1),
            primaryPartNames = key.primaryPartNames(),
            secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4, p5),
            secondaryPartNames = key.secondaryPartNames(),
        ).hashEntryRef(name)
        CacheStorage.Set -> typedPrimarySecondaryKey(
            primaryPartArgs = key.encodePrimaryParts(p1),
            primaryPartNames = key.primaryPartNames(),
            secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4, p5),
            secondaryPartNames = key.secondaryPartNames(),
        ).setEntryRef(name)
    } as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(value: P1): StoredCachePartRef<S> = when (storedAs) {
        CacheStorage.HashMap -> groupedHashPrimaryPartRef(name, key, value)
        CacheStorage.Set -> groupedSetPrimaryPartRef(name, key, value)
    } as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimarySecondaryKey6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S>(
    val name: String,
    val key: KeyPartCompositionGroup6<P1, P2, P3, P4, P5, P6>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage {

    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1): StoredCacheEntryRef<S> = when (storedAs) {
        CacheStorage.HashMap -> groupedHashEntryRef(name, key, p1)
        CacheStorage.Set -> groupedSetEntryRef(name, key, p1)
    } as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): StoredCacheEntryRef<S> = when (storedAs) {
        CacheStorage.HashMap -> typedHashPrimarySecondaryKey(
            primaryPartArgs = key.encodePrimaryParts(p1),
            primaryPartNames = key.primaryPartNames(),
            secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4, p5, p6),
            secondaryPartNames = key.secondaryPartNames(),
        ).hashEntryRef(name)
        CacheStorage.Set -> typedPrimarySecondaryKey(
            primaryPartArgs = key.encodePrimaryParts(p1),
            primaryPartNames = key.primaryPartNames(),
            secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4, p5, p6),
            secondaryPartNames = key.secondaryPartNames(),
        ).setEntryRef(name)
    } as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(value: P1): StoredCachePartRef<S> = when (storedAs) {
        CacheStorage.HashMap -> groupedHashPrimaryPartRef(name, key, value)
        CacheStorage.Set -> groupedSetPrimaryPartRef(name, key, value)
    } as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, S> TypedPrimarySecondaryKey2<P1, P2, S>.keyPart(
    value: P1,
    vararg secondaryParts: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    if (secondaryParts.isEmpty()) {
        groupedHashPrimaryPartRef(name, key, value)
    } else {
        groupedHashPrimaryPartRef(
            name = name,
            key = key,
            primaryValue = value,
            secondaryPatternPartArgs = buildSecondaryPatternPartArgs(key.secondaryParts(), secondaryParts),
        )
    }

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, S> TypedPrimarySecondaryKey2<P1, P2, S>.keyPart(
    vararg selections: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    selectedGroupedHashPartRef(name, key, selections)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, S> TypedPrimarySecondaryKey3<P1, P2, P3, S>.keyPart(
    value: P1,
    vararg secondaryParts: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    if (secondaryParts.isEmpty()) {
        groupedHashPrimaryPartRef(name, key, value)
    } else {
        groupedHashPrimaryPartRef(
            name = name,
            key = key,
            primaryValue = value,
            secondaryPatternPartArgs = buildSecondaryPatternPartArgs(key.secondaryParts(), secondaryParts),
        )
    }

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, S> TypedPrimarySecondaryKey3<P1, P2, P3, S>.keyPart(
    vararg selections: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    selectedGroupedHashPartRef(name, key, selections)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, S> TypedPrimarySecondaryKey4<P1, P2, P3, P4, S>.keyPart(
    value: P1,
    vararg secondaryParts: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    if (secondaryParts.isEmpty()) {
        groupedHashPrimaryPartRef(name, key, value)
    } else {
        groupedHashPrimaryPartRef(
            name = name,
            key = key,
            primaryValue = value,
            secondaryPatternPartArgs = buildSecondaryPatternPartArgs(key.secondaryParts(), secondaryParts),
        )
    }

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, S> TypedPrimarySecondaryKey4<P1, P2, P3, P4, S>.keyPart(
    vararg selections: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    selectedGroupedHashPartRef(name, key, selections)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S> TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, S>.keyPart(
    value: P1,
    vararg secondaryParts: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    if (secondaryParts.isEmpty()) {
        groupedHashPrimaryPartRef(name, key, value)
    } else {
        groupedHashPrimaryPartRef(
            name = name,
            key = key,
            primaryValue = value,
            secondaryPatternPartArgs = buildSecondaryPatternPartArgs(key.secondaryParts(), secondaryParts),
        )
    }

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S> TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, S>.keyPart(
    vararg selections: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    selectedGroupedHashPartRef(name, key, selections)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S> TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, S>.keyPart(
    value: P1,
    vararg secondaryParts: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    if (secondaryParts.isEmpty()) {
        groupedHashPrimaryPartRef(name, key, value)
    } else {
        groupedHashPrimaryPartRef(
            name = name,
            key = key,
            primaryValue = value,
            secondaryPatternPartArgs = buildSecondaryPatternPartArgs(key.secondaryParts(), secondaryParts),
        )
    }

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S> TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, S>.keyPart(
    vararg selections: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    selectedGroupedHashPartRef(name, key, selections)

@Deprecated(
    message = "Use TypedPrimarySecondaryKey2<P1, P2, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimarySecondaryKey2<P1, P2, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimarySecondaryKey2", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapStoredCache2<P1, P2> = TypedPrimarySecondaryKey2<P1, P2, CacheStorage.HashMap>

@Deprecated(
    message = "Use TypedPrimarySecondaryKey2<P1, P2, CacheStorage.Set> instead.",
    replaceWith = ReplaceWith("TypedPrimarySecondaryKey2<P1, P2, CacheStorage.Set>", imports = ["com.github.dave08.kacheable.TypedPrimarySecondaryKey2", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias SetStoredCache2<P1, P2> = TypedPrimarySecondaryKey2<P1, P2, CacheStorage.Set>

@Deprecated(
    message = "Use TypedPrimarySecondaryKey3<P1, P2, P3, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimarySecondaryKey3<P1, P2, P3, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimarySecondaryKey3", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapStoredCache3<P1, P2, P3> = TypedPrimarySecondaryKey3<P1, P2, P3, CacheStorage.HashMap>

@Deprecated(
    message = "Use TypedPrimarySecondaryKey4<P1, P2, P3, P4, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimarySecondaryKey4<P1, P2, P3, P4, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimarySecondaryKey4", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapStoredCache4<P1, P2, P3, P4> = TypedPrimarySecondaryKey4<P1, P2, P3, P4, CacheStorage.HashMap>

@Deprecated(
    message = "Use TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimarySecondaryKey5", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapStoredCache5<P1, P2, P3, P4, P5> = TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, CacheStorage.HashMap>

@Deprecated(
    message = "Use TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimarySecondaryKey6", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapStoredCache6<P1, P2, P3, P4, P5, P6> =
    TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.HashMap>
