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
): CacheEntryPartRef {
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

    return SimpleCacheEntryPartRef(
        name = name,
        args = joinArgs(*primaryPartArgs.toTypedArray()),
        storageLayout = CacheStorageLayout.HashValue,
        secondaryPatternPartArgs = secondarySelections
            .takeIf { it.isNotEmpty() }
            ?.let { buildSecondaryPatternPartArgs(secondaryParts, it) },
        cacheArgs = cacheArgs(
            primaryPartArgs = primaryPartArgs,
            primaryPartNames = primaryParts.map { it.name },
        ),
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

@ExperimentalKacheableApi
data class HashMapPrimaryKey<P1 : Any>(
    val name: String,
    val key: KeyPart<P1>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name,
            cacheArgs(
                primaryPartArgs = listOf(key.encodePart(p1)),
                primaryPartNames = listOf(key.name),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.encode(value),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(
                primaryPartArgs = listOf(key.encodePart(value)),
                primaryPartNames = listOf(key.name),
            ),
        )
}

@ExperimentalKacheableApi
data class HashMapPrimaryKey2<P1 : Any, P2 : Any>(
    val name: String,
    val key: KeyPartComposition2<P1, P2>,
) {
    fun key(p1: P1, p2: P2): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, cacheArgs(key.encodeParts(p1, p2), key.partNames()))

    fun keyPart(p1: P1, p2: P2): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.encode(p1, p2),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(key.encodeParts(p1, p2), key.partNames()),
        )
}

@ExperimentalKacheableApi
data class HashMapPrimaryKey3<P1 : Any, P2 : Any, P3 : Any>(
    val name: String,
    val key: KeyPartComposition3<P1, P2, P3>,
) {
    fun key(p1: P1, p2: P2, p3: P3): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, cacheArgs(key.encodeParts(p1, p2, p3), key.partNames()))

    fun keyPart(p1: P1, p2: P2, p3: P3): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.encode(p1, p2, p3),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(key.encodeParts(p1, p2, p3), key.partNames()),
        )
}

@ExperimentalKacheableApi
data class HashMapPrimaryKey4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val name: String,
    val key: KeyPartComposition4<P1, P2, P3, P4>,
) {
    fun key(p1: P1, p2: P2, p3: P3, p4: P4): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, cacheArgs(key.encodeParts(p1, p2, p3, p4), key.partNames()))

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.encode(p1, p2, p3, p4),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(key.encodeParts(p1, p2, p3, p4), key.partNames()),
        )
}

@ExperimentalKacheableApi
data class HashMapPrimaryKey5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val name: String,
    val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
) {
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, cacheArgs(key.encodeParts(p1, p2, p3, p4, p5), key.partNames()))

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.encode(p1, p2, p3, p4, p5),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(key.encodeParts(p1, p2, p3, p4, p5), key.partNames()),
        )
}

@ExperimentalKacheableApi
data class HashMapPrimaryKey6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val name: String,
    val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
) {
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, cacheArgs(key.encodeParts(p1, p2, p3, p4, p5, p6), key.partNames()))

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.encode(p1, p2, p3, p4, p5, p6),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(key.encodeParts(p1, p2, p3, p4, p5, p6), key.partNames()),
        )
}

@ExperimentalKacheableApi
data class HashMapStoredCache2<P1 : Any, P2 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup2<P1, P2>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name,
            cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
            ),
        )

    fun key(p1: P1, p2: P2): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
                secondaryPartArgs = key.encodeSecondaryParts(p2),
                secondaryPartNames = key.secondaryPartNames(),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.primary.encode(value),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(value),
                primaryPartNames = key.primaryPartNames(),
            ),
        )

    fun keyPart(value: P1, vararg secondaryParts: KeyPartValue): CacheEntryPartRef =
        if (secondaryParts.isEmpty()) {
            keyPart(value)
        } else {
            SimpleCacheEntryPartRef(
                name = name,
                args = key.primary.encode(value),
                storageLayout = CacheStorageLayout.HashValue,
                secondaryPatternPartArgs = buildSecondaryPatternPartArgs(key.secondaryParts(), secondaryParts),
                cacheArgs = cacheArgs(
                    primaryPartArgs = key.encodePrimaryParts(value),
                    primaryPartNames = key.primaryPartNames(),
                ),
            )
        }

    fun keyPart(vararg selections: KeyPartValue): CacheEntryPartRef =
        buildLayeredHashPartRef(name, listOf(key.primary), key.secondaryParts(), selections)
}

@ExperimentalKacheableApi
data class HashMapStoredCache3<P1 : Any, P2 : Any, P3 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup3<P1, P2, P3>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name,
            cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
            ),
        )

    fun key(p1: P1, p2: P2, p3: P3): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
                secondaryPartArgs = key.encodeSecondaryParts(p2, p3),
                secondaryPartNames = key.secondaryPartNames(),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.primary.encode(value),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(value),
                primaryPartNames = key.primaryPartNames(),
            ),
        )

    fun keyPart(value: P1, vararg secondaryParts: KeyPartValue): CacheEntryPartRef =
        if (secondaryParts.isEmpty()) {
            keyPart(value)
        } else {
            SimpleCacheEntryPartRef(
                name = name,
                args = key.primary.encode(value),
                storageLayout = CacheStorageLayout.HashValue,
                secondaryPatternPartArgs = buildSecondaryPatternPartArgs(key.secondaryParts(), secondaryParts),
                cacheArgs = cacheArgs(
                    primaryPartArgs = key.encodePrimaryParts(value),
                    primaryPartNames = key.primaryPartNames(),
                ),
            )
        }

    fun keyPart(vararg selections: KeyPartValue): CacheEntryPartRef =
        buildLayeredHashPartRef(name, listOf(key.primary), key.secondaryParts(), selections)
}

@ExperimentalKacheableApi
data class HashMapStoredCache4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup4<P1, P2, P3, P4>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name,
            cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
            ),
        )

    fun key(p1: P1, p2: P2, p3: P3, p4: P4): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
                secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4),
                secondaryPartNames = key.secondaryPartNames(),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.primary.encode(value),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(value),
                primaryPartNames = key.primaryPartNames(),
            ),
        )

    fun keyPart(value: P1, vararg secondaryParts: KeyPartValue): CacheEntryPartRef =
        if (secondaryParts.isEmpty()) {
            keyPart(value)
        } else {
            SimpleCacheEntryPartRef(
                name = name,
                args = key.primary.encode(value),
                storageLayout = CacheStorageLayout.HashValue,
                secondaryPatternPartArgs = buildSecondaryPatternPartArgs(key.secondaryParts(), secondaryParts),
                cacheArgs = cacheArgs(
                    primaryPartArgs = key.encodePrimaryParts(value),
                    primaryPartNames = key.primaryPartNames(),
                ),
            )
        }

    fun keyPart(vararg selections: KeyPartValue): CacheEntryPartRef =
        buildLayeredHashPartRef(name, listOf(key.primary), key.secondaryParts(), selections)
}

@ExperimentalKacheableApi
data class HashMapStoredCache5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup5<P1, P2, P3, P4, P5>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name,
            cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
            ),
        )

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
                secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4, p5),
                secondaryPartNames = key.secondaryPartNames(),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.primary.encode(value),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(value),
                primaryPartNames = key.primaryPartNames(),
            ),
        )

    fun keyPart(value: P1, vararg secondaryParts: KeyPartValue): CacheEntryPartRef =
        if (secondaryParts.isEmpty()) {
            keyPart(value)
        } else {
            SimpleCacheEntryPartRef(
                name = name,
                args = key.primary.encode(value),
                storageLayout = CacheStorageLayout.HashValue,
                secondaryPatternPartArgs = buildSecondaryPatternPartArgs(key.secondaryParts(), secondaryParts),
                cacheArgs = cacheArgs(
                    primaryPartArgs = key.encodePrimaryParts(value),
                    primaryPartNames = key.primaryPartNames(),
                ),
            )
        }

    fun keyPart(vararg selections: KeyPartValue): CacheEntryPartRef =
        buildLayeredHashPartRef(name, listOf(key.primary), key.secondaryParts(), selections)
}

@ExperimentalKacheableApi
data class HashMapStoredCache6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup6<P1, P2, P3, P4, P5, P6>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name,
            cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
            ),
        )

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
                secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4, p5, p6),
                secondaryPartNames = key.secondaryPartNames(),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        SimpleCacheEntryPartRef(
            name = name,
            args = key.primary.encode(value),
            storageLayout = CacheStorageLayout.HashValue,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(value),
                primaryPartNames = key.primaryPartNames(),
            ),
        )

    fun keyPart(value: P1, vararg secondaryParts: KeyPartValue): CacheEntryPartRef =
        if (secondaryParts.isEmpty()) {
            keyPart(value)
        } else {
            SimpleCacheEntryPartRef(
                name = name,
                args = key.primary.encode(value),
                storageLayout = CacheStorageLayout.HashValue,
                secondaryPatternPartArgs = buildSecondaryPatternPartArgs(key.secondaryParts(), secondaryParts),
                cacheArgs = cacheArgs(
                    primaryPartArgs = key.encodePrimaryParts(value),
                    primaryPartNames = key.primaryPartNames(),
                ),
            )
        }

    fun keyPart(vararg selections: KeyPartValue): CacheEntryPartRef =
        buildLayeredHashPartRef(name, listOf(key.primary), key.secondaryParts(), selections)
}
