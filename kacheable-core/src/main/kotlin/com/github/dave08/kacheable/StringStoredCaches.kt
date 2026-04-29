@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

private fun stringPartRef(
    name: String,
    args: CacheArgs,
    cacheArgs: PrimarySecondaryCacheArgs = PrimarySecondaryCacheArgs(args),
): CacheEntryPartRef =
    SimpleCacheEntryPartRef(
        name = name,
        args = args,
        storageLayout = CacheStorageLayout.StringValue,
        cacheArgs = cacheArgs,
    )

@ExperimentalKacheableApi
data class StringPrimaryKey<P1 : Any>(
    val name: String,
    val key: KeyPart<P1>,
) {
    fun key(p1: P1): StringCacheEntryRef =
        StringCacheEntryRef(
            name,
            cacheArgs(
                primaryPartArgs = listOf(key.encodePart(p1)),
                primaryPartNames = listOf(key.name),
            ),
        )

    fun keyPart(value: P1): CacheEntryPartRef =
        stringPartRef(
            name = name,
            args = key.encode(value),
            cacheArgs = cacheArgs(
                primaryPartArgs = listOf(key.encodePart(value)),
                primaryPartNames = listOf(key.name),
            ),
        )
}

@ExperimentalKacheableApi
data class StringPrimaryKey2<P1 : Any, P2 : Any>(
    val name: String,
    val key: KeyPartComposition2<P1, P2>,
) {
    fun key(p1: P1, p2: P2): StringCacheEntryRef =
        StringCacheEntryRef(name, cacheArgs(key.encodeParts(p1, p2), key.partNames()))

    fun keyPart(p1: P1, p2: P2): CacheEntryPartRef =
        stringPartRef(name, key.encode(p1, p2), cacheArgs(key.encodeParts(p1, p2), key.partNames()))
}

@ExperimentalKacheableApi
data class StringPrimaryKey3<P1 : Any, P2 : Any, P3 : Any>(
    val name: String,
    val key: KeyPartComposition3<P1, P2, P3>,
) {
    fun key(p1: P1, p2: P2, p3: P3): StringCacheEntryRef =
        StringCacheEntryRef(name, cacheArgs(key.encodeParts(p1, p2, p3), key.partNames()))

    fun keyPart(p1: P1, p2: P2, p3: P3): CacheEntryPartRef =
        stringPartRef(name, key.encode(p1, p2, p3), cacheArgs(key.encodeParts(p1, p2, p3), key.partNames()))
}

@ExperimentalKacheableApi
data class StringPrimaryKey4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val name: String,
    val key: KeyPartComposition4<P1, P2, P3, P4>,
) {
    fun key(p1: P1, p2: P2, p3: P3, p4: P4): StringCacheEntryRef =
        StringCacheEntryRef(name, cacheArgs(key.encodeParts(p1, p2, p3, p4), key.partNames()))

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4): CacheEntryPartRef =
        stringPartRef(name, key.encode(p1, p2, p3, p4), cacheArgs(key.encodeParts(p1, p2, p3, p4), key.partNames()))
}

@ExperimentalKacheableApi
data class StringPrimaryKey5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val name: String,
    val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
) {
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): StringCacheEntryRef =
        StringCacheEntryRef(name, cacheArgs(key.encodeParts(p1, p2, p3, p4, p5), key.partNames()))

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheEntryPartRef =
        stringPartRef(
            name,
            key.encode(p1, p2, p3, p4, p5),
            cacheArgs(key.encodeParts(p1, p2, p3, p4, p5), key.partNames()),
        )
}

@ExperimentalKacheableApi
data class StringPrimaryKey6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val name: String,
    val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
) {
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): StringCacheEntryRef =
        StringCacheEntryRef(name, cacheArgs(key.encodeParts(p1, p2, p3, p4, p5, p6), key.partNames()))

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheEntryPartRef =
        stringPartRef(
            name,
            key.encode(p1, p2, p3, p4, p5, p6),
            cacheArgs(key.encodeParts(p1, p2, p3, p4, p5, p6), key.partNames()),
        )
}
