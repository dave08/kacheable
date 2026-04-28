@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
data class HashMapPrimaryKey<P1 : Any>(
    val name: String,
    val key: KeyPart<P1>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.encode(p1)))

    fun keyPart(value: P1): CacheEntryPartRef =
        SimpleCacheEntryPartRef(name, key.encode(value), CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapPrimaryKey2<P1 : Any, P2 : Any>(
    val name: String,
    val key: KeyPartComposition2<P1, P2>,
) {
    fun key(p1: P1, p2: P2): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.encode(p1, p2)))

    fun keyPart(p1: P1, p2: P2): CacheEntryPartRef =
        SimpleCacheEntryPartRef(name, key.encode(p1, p2), CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapPrimaryKey3<P1 : Any, P2 : Any, P3 : Any>(
    val name: String,
    val key: KeyPartComposition3<P1, P2, P3>,
) {
    fun key(p1: P1, p2: P2, p3: P3): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.encode(p1, p2, p3)))

    fun keyPart(p1: P1, p2: P2, p3: P3): CacheEntryPartRef =
        SimpleCacheEntryPartRef(name, key.encode(p1, p2, p3), CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapPrimaryKey4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val name: String,
    val key: KeyPartComposition4<P1, P2, P3, P4>,
) {
    fun key(p1: P1, p2: P2, p3: P3, p4: P4): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.encode(p1, p2, p3, p4)))

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4): CacheEntryPartRef =
        SimpleCacheEntryPartRef(name, key.encode(p1, p2, p3, p4), CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapPrimaryKey5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val name: String,
    val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
) {
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.encode(p1, p2, p3, p4, p5)))

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheEntryPartRef =
        SimpleCacheEntryPartRef(name, key.encode(p1, p2, p3, p4, p5), CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapPrimaryKey6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val name: String,
    val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
) {
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.encode(p1, p2, p3, p4, p5, p6)))

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheEntryPartRef =
        SimpleCacheEntryPartRef(name, key.encode(p1, p2, p3, p4, p5, p6), CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache2<P1 : Any, P2 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup2<P1, P2>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.primary.encode(p1)))

    fun key(p1: P1, p2: P2): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            cacheArgs = PrimarySecondaryCacheArgs(
                primary = key.primary.encode(p1),
                secondary = key.secondary.encode(p2),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        groupedEntryPartRef(name, key.primary.encode(value), key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache3<P1 : Any, P2 : Any, P3 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup3<P1, P2, P3>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.primary.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            cacheArgs = PrimarySecondaryCacheArgs(
                primary = key.primary.encode(p1),
                secondary = key.secondary.encode(p2, p3),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        groupedEntryPartRef(name, key.primary.encode(value), key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup4<P1, P2, P3, P4>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.primary.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3, p4: P4): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            cacheArgs = PrimarySecondaryCacheArgs(
                primary = key.primary.encode(p1),
                secondary = key.secondary.encode(p2, p3, p4),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        groupedEntryPartRef(name, key.primary.encode(value), key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup5<P1, P2, P3, P4, P5>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.primary.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            cacheArgs = PrimarySecondaryCacheArgs(
                primary = key.primary.encode(p1),
                secondary = key.secondary.encode(p2, p3, p4, p5),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        groupedEntryPartRef(name, key.primary.encode(value), key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup6<P1, P2, P3, P4, P5, P6>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, PrimarySecondaryCacheArgs(primary = key.primary.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            cacheArgs = PrimarySecondaryCacheArgs(
                primary = key.primary.encode(p1),
                secondary = key.secondary.encode(p2, p3, p4, p5, p6),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        groupedEntryPartRef(name, key.primary.encode(value), key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
}
