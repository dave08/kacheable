@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
data class HashMapMainKey<P1 : Any>(
    val name: String,
    val key: MainKeyPart<P1>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.encode(p1)))

    fun keyPart(value: P1): CacheEntryPartRef =
        SimpleCacheEntryPartRef(name, key.encode(value), CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache2<P1 : Any, P2 : Any>(
    val name: String,
    val key: MainSecondaryKey2<P1, P2>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.main.encode(p1)))

    fun key(p1: P1, p2: P2): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        groupedEntryPartRef(name, key.main.encode(value), key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache3<P1 : Any, P2 : Any, P3 : Any>(
    val name: String,
    val key: MainSecondaryKey3<P1, P2, P3>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.main.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2, p3),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        groupedEntryPartRef(name, key.main.encode(value), key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val name: String,
    val key: MainSecondaryKey4<P1, P2, P3, P4>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.main.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3, p4: P4): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2, p3, p4),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        groupedEntryPartRef(name, key.main.encode(value), key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val name: String,
    val key: MainSecondaryKey5<P1, P2, P3, P4, P5>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.main.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2, p3, p4, p5),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        groupedEntryPartRef(name, key.main.encode(value), key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val name: String,
    val key: MainSecondaryKey6<P1, P2, P3, P4, P5, P6>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.main.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2, p3, p4, p5, p6),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        groupedEntryPartRef(name, key.main.encode(value), key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
}
