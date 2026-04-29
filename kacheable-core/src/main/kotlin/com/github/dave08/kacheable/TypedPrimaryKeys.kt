@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

private fun requireValueStorage(storedAs: CacheStorage) {
    require(storedAs == CacheStorage.String || storedAs == CacheStorage.HashMap) {
        "TypedPrimaryKey supports value storage only. Use set primary-secondary keys for CacheStorage.Set."
    }
}

private fun valueStorageLayout(storedAs: CacheStorage): CacheStorageLayout =
    when (storedAs) {
        CacheStorage.String -> CacheStorageLayout.StringValue
        CacheStorage.HashMap -> CacheStorageLayout.HashValue
        else -> throw IllegalArgumentException("Unsupported value storage: $storedAs")
    }

private fun valueEntryRef(
    name: String,
    storedAs: CacheStorage,
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
): StoredCacheEntryRef<*> = when (storedAs) {
    CacheStorage.String -> typedPrimaryKey(primaryPartArgs, primaryPartNames).stringEntryRef(name)
    CacheStorage.HashMap -> typedPrimaryKey(primaryPartArgs, primaryPartNames).hashEntryRef(name)
    else -> throw UnsupportedOperationException("Exact value entry refs are not supported for storage $storedAs.")
}

private fun valuePartRef(
    name: String,
    storedAs: CacheStorage,
    args: CacheArgs,
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
): CacheEntryPartRef = typedPrimaryKey(
    primaryPartArgs = primaryPartArgs,
    primaryPartNames = primaryPartNames,
).partRef(
    name = name,
    args = args,
    storageLayout = valueStorageLayout(storedAs),
)

@ExperimentalKacheableApi
data class TypedPrimaryKey<P1 : Any>(
    val name: String,
    val key: KeyPart<P1>,
    val storedAs: CacheStorage,
) {
    init {
        requireValueStorage(storedAs)
    }

    fun key(p1: P1): StoredCacheEntryRef<*> =
        valueEntryRef(name, storedAs, listOf(key.encodePart(p1)), listOf(key.name))

    fun keyPart(value: P1): CacheEntryPartRef =
        valuePartRef(name, storedAs, key.encode(value), listOf(key.encodePart(value)), listOf(key.name))
}

@ExperimentalKacheableApi
data class TypedPrimaryKey2<P1 : Any, P2 : Any>(
    val name: String,
    val key: KeyPartComposition2<P1, P2>,
    val storedAs: CacheStorage,
) {
    init {
        requireValueStorage(storedAs)
    }

    fun key(p1: P1, p2: P2): StoredCacheEntryRef<*> =
        valueEntryRef(name, storedAs, key.encodeParts(p1, p2), key.partNames())

    fun keyPart(p1: P1, p2: P2): CacheEntryPartRef =
        valuePartRef(name, storedAs, key.encode(p1, p2), key.encodeParts(p1, p2), key.partNames())
}

@ExperimentalKacheableApi
data class TypedPrimaryKey3<P1 : Any, P2 : Any, P3 : Any>(
    val name: String,
    val key: KeyPartComposition3<P1, P2, P3>,
    val storedAs: CacheStorage,
) {
    init {
        requireValueStorage(storedAs)
    }

    fun key(p1: P1, p2: P2, p3: P3): StoredCacheEntryRef<*> =
        valueEntryRef(name, storedAs, key.encodeParts(p1, p2, p3), key.partNames())

    fun keyPart(p1: P1, p2: P2, p3: P3): CacheEntryPartRef =
        valuePartRef(name, storedAs, key.encode(p1, p2, p3), key.encodeParts(p1, p2, p3), key.partNames())
}

@ExperimentalKacheableApi
data class TypedPrimaryKey4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val name: String,
    val key: KeyPartComposition4<P1, P2, P3, P4>,
    val storedAs: CacheStorage,
) {
    init {
        requireValueStorage(storedAs)
    }

    fun key(p1: P1, p2: P2, p3: P3, p4: P4): StoredCacheEntryRef<*> =
        valueEntryRef(name, storedAs, key.encodeParts(p1, p2, p3, p4), key.partNames())

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4): CacheEntryPartRef =
        valuePartRef(name, storedAs, key.encode(p1, p2, p3, p4), key.encodeParts(p1, p2, p3, p4), key.partNames())
}

@ExperimentalKacheableApi
data class TypedPrimaryKey5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val name: String,
    val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
    val storedAs: CacheStorage,
) {
    init {
        requireValueStorage(storedAs)
    }

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): StoredCacheEntryRef<*> =
        valueEntryRef(name, storedAs, key.encodeParts(p1, p2, p3, p4, p5), key.partNames())

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheEntryPartRef =
        valuePartRef(name, storedAs, key.encode(p1, p2, p3, p4, p5), key.encodeParts(p1, p2, p3, p4, p5), key.partNames())
}

@ExperimentalKacheableApi
data class TypedPrimaryKey6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val name: String,
    val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    val storedAs: CacheStorage,
) {
    init {
        requireValueStorage(storedAs)
    }

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): StoredCacheEntryRef<*> =
        valueEntryRef(name, storedAs, key.encodeParts(p1, p2, p3, p4, p5, p6), key.partNames())

    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheEntryPartRef =
        valuePartRef(
            name,
            storedAs,
            key.encode(p1, p2, p3, p4, p5, p6),
            key.encodeParts(p1, p2, p3, p4, p5, p6),
            key.partNames(),
        )
}

@Deprecated("Use TypedPrimaryKey instead.")
typealias StringPrimaryKey<P1> = TypedPrimaryKey<P1>

@Deprecated("Use TypedPrimaryKey instead.")
typealias HashMapPrimaryKey<P1> = TypedPrimaryKey<P1>

@Deprecated("Use TypedPrimaryKey2 instead.")
typealias StringPrimaryKey2<P1, P2> = TypedPrimaryKey2<P1, P2>

@Deprecated("Use TypedPrimaryKey2 instead.")
typealias HashMapPrimaryKey2<P1, P2> = TypedPrimaryKey2<P1, P2>

@Deprecated("Use TypedPrimaryKey3 instead.")
typealias StringPrimaryKey3<P1, P2, P3> = TypedPrimaryKey3<P1, P2, P3>

@Deprecated("Use TypedPrimaryKey3 instead.")
typealias HashMapPrimaryKey3<P1, P2, P3> = TypedPrimaryKey3<P1, P2, P3>

@Deprecated("Use TypedPrimaryKey4 instead.")
typealias StringPrimaryKey4<P1, P2, P3, P4> = TypedPrimaryKey4<P1, P2, P3, P4>

@Deprecated("Use TypedPrimaryKey4 instead.")
typealias HashMapPrimaryKey4<P1, P2, P3, P4> = TypedPrimaryKey4<P1, P2, P3, P4>

@Deprecated("Use TypedPrimaryKey5 instead.")
typealias StringPrimaryKey5<P1, P2, P3, P4, P5> = TypedPrimaryKey5<P1, P2, P3, P4, P5>

@Deprecated("Use TypedPrimaryKey5 instead.")
typealias HashMapPrimaryKey5<P1, P2, P3, P4, P5> = TypedPrimaryKey5<P1, P2, P3, P4, P5>

@Deprecated("Use TypedPrimaryKey6 instead.")
typealias StringPrimaryKey6<P1, P2, P3, P4, P5, P6> = TypedPrimaryKey6<P1, P2, P3, P4, P5, P6>

@Deprecated("Use TypedPrimaryKey6 instead.")
typealias HashMapPrimaryKey6<P1, P2, P3, P4, P5, P6> = TypedPrimaryKey6<P1, P2, P3, P4, P5, P6>
