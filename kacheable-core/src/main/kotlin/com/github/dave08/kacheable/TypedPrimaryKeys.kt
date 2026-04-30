@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

private fun resolvedEntryRef(
    name: String,
    storedAs: CacheStorage,
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
): StoredCacheEntryRef<*> = when (storedAs) {
    CacheStorage.String -> typedPrimaryKey(primaryPartArgs, primaryPartNames).stringEntryRef(name)
    CacheStorage.HashMap -> typedPrimaryKey(primaryPartArgs, primaryPartNames).hashEntryRef(name)
    CacheStorage.Set -> typedPrimaryKey(primaryPartArgs, primaryPartNames).setEntryRef(name)
}

private fun resolvedPrimaryPartRef(
    name: String,
    storedAs: CacheStorage,
    args: CacheArgs,
    primaryPartArgs: List<CacheArgs>,
    primaryPartNames: List<String?>,
) : StoredCachePartRef<*> = when (storedAs) {
    CacheStorage.String -> CachePartRef(
        name = name,
        args = args,
        cacheArgs = cacheArgs(
            primaryPartArgs = primaryPartArgs,
            primaryPartNames = primaryPartNames,
        ),
        storage = CacheStorage.String,
    )
    CacheStorage.HashMap -> CachePartRef(
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

@ExperimentalKacheableApi
data class TypedPrimaryKey<P1 : Any, S>(
    val name: String,
    val key: KeyPart<P1>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimaryKeyStorage {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1): StoredCacheEntryRef<S> =
        resolvedEntryRef(name, storedAs, listOf(key.encodePart(p1)), listOf(key.name)) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(value: P1): StoredCachePartRef<S> =
        resolvedPrimaryPartRef(name, storedAs, key.encode(value), listOf(key.encodePart(value)), listOf(key.name)) as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimaryKey2<P1 : Any, P2 : Any, S>(
    val name: String,
    val key: KeyPartComposition2<P1, P2>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsValueReturn {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2): StoredCacheEntryRef<S> =
        resolvedEntryRef(name, storedAs, key.encodeParts(p1, p2), key.partNames()) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(p1: P1, p2: P2): StoredCachePartRef<S> =
        resolvedPrimaryPartRef(name, storedAs, key.encode(p1, p2), key.encodeParts(p1, p2), key.partNames()) as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimaryKey3<P1 : Any, P2 : Any, P3 : Any, S>(
    val name: String,
    val key: KeyPartComposition3<P1, P2, P3>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsValueReturn {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3): StoredCacheEntryRef<S> =
        resolvedEntryRef(name, storedAs, key.encodeParts(p1, p2, p3), key.partNames()) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(p1: P1, p2: P2, p3: P3): StoredCachePartRef<S> =
        resolvedPrimaryPartRef(name, storedAs, key.encode(p1, p2, p3), key.encodeParts(p1, p2, p3), key.partNames()) as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimaryKey4<P1 : Any, P2 : Any, P3 : Any, P4 : Any, S>(
    val name: String,
    val key: KeyPartComposition4<P1, P2, P3, P4>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsValueReturn {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3, p4: P4): StoredCacheEntryRef<S> =
        resolvedEntryRef(name, storedAs, key.encodeParts(p1, p2, p3, p4), key.partNames()) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4): StoredCachePartRef<S> =
        resolvedPrimaryPartRef(name, storedAs, key.encode(p1, p2, p3, p4), key.encodeParts(p1, p2, p3, p4), key.partNames()) as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimaryKey5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S>(
    val name: String,
    val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsValueReturn {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): StoredCacheEntryRef<S> =
        resolvedEntryRef(name, storedAs, key.encodeParts(p1, p2, p3, p4, p5), key.partNames()) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): StoredCachePartRef<S> =
        resolvedPrimaryPartRef(name, storedAs, key.encode(p1, p2, p3, p4, p5), key.encodeParts(p1, p2, p3, p4, p5), key.partNames()) as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimaryKey6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S>(
    val name: String,
    val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsValueReturn {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): StoredCacheEntryRef<S> =
        resolvedEntryRef(name, storedAs, key.encodeParts(p1, p2, p3, p4, p5, p6), key.partNames()) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): StoredCachePartRef<S> =
        resolvedPrimaryPartRef(
            name,
            storedAs,
            key.encode(p1, p2, p3, p4, p5, p6),
            key.encodeParts(p1, p2, p3, p4, p5, p6),
            key.partNames(),
        ) as StoredCachePartRef<S>
}

@Deprecated(
    message = "Use TypedPrimaryKey<P1, CacheStorage.String> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey<P1, CacheStorage.String>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias StringPrimaryKey<P1> = TypedPrimaryKey<P1, CacheStorage.String>

@Deprecated(
    message = "Use TypedPrimaryKey<P1, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey<P1, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapPrimaryKey<P1> = TypedPrimaryKey<P1, CacheStorage.HashMap>

@Deprecated(
    message = "Use TypedPrimaryKey<P1, CacheStorage.Set> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey<P1, CacheStorage.Set>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias SetPrimaryKey<P1> = TypedPrimaryKey<P1, CacheStorage.Set>

@Deprecated(
    message = "Use TypedPrimaryKey2<P1, P2, CacheStorage.String> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey2<P1, P2, CacheStorage.String>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey2", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias StringPrimaryKey2<P1, P2> = TypedPrimaryKey2<P1, P2, CacheStorage.String>

@Deprecated(
    message = "Use TypedPrimaryKey2<P1, P2, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey2<P1, P2, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey2", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapPrimaryKey2<P1, P2> = TypedPrimaryKey2<P1, P2, CacheStorage.HashMap>

@Deprecated(
    message = "Use TypedPrimaryKey3<P1, P2, P3, CacheStorage.String> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey3<P1, P2, P3, CacheStorage.String>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey3", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias StringPrimaryKey3<P1, P2, P3> = TypedPrimaryKey3<P1, P2, P3, CacheStorage.String>

@Deprecated(
    message = "Use TypedPrimaryKey3<P1, P2, P3, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey3<P1, P2, P3, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey3", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapPrimaryKey3<P1, P2, P3> = TypedPrimaryKey3<P1, P2, P3, CacheStorage.HashMap>

@Deprecated(
    message = "Use TypedPrimaryKey4<P1, P2, P3, P4, CacheStorage.String> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey4<P1, P2, P3, P4, CacheStorage.String>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey4", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias StringPrimaryKey4<P1, P2, P3, P4> = TypedPrimaryKey4<P1, P2, P3, P4, CacheStorage.String>

@Deprecated(
    message = "Use TypedPrimaryKey4<P1, P2, P3, P4, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey4<P1, P2, P3, P4, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey4", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapPrimaryKey4<P1, P2, P3, P4> = TypedPrimaryKey4<P1, P2, P3, P4, CacheStorage.HashMap>

@Deprecated(
    message = "Use TypedPrimaryKey5<P1, P2, P3, P4, P5, CacheStorage.String> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey5<P1, P2, P3, P4, P5, CacheStorage.String>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey5", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias StringPrimaryKey5<P1, P2, P3, P4, P5> = TypedPrimaryKey5<P1, P2, P3, P4, P5, CacheStorage.String>

@Deprecated(
    message = "Use TypedPrimaryKey5<P1, P2, P3, P4, P5, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey5<P1, P2, P3, P4, P5, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey5", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapPrimaryKey5<P1, P2, P3, P4, P5> = TypedPrimaryKey5<P1, P2, P3, P4, P5, CacheStorage.HashMap>

@Deprecated(
    message = "Use TypedPrimaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.String> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.String>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey6", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias StringPrimaryKey6<P1, P2, P3, P4, P5, P6> =
    TypedPrimaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.String>

@Deprecated(
    message = "Use TypedPrimaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("TypedPrimaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.TypedPrimaryKey6", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapPrimaryKey6<P1, P2, P3, P4, P5, P6> =
    TypedPrimaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.HashMap>
