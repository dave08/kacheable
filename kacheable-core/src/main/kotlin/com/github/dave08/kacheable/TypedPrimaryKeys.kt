@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.storage.resolveStoredPrimaryEntryRef
import com.github.dave08.kacheable.internal.storage.resolveStoredPrimaryPartRef

@ExperimentalKacheableApi
data class TypedPrimaryKey<P1 : Any, S>(
    val name: String,
    val key: KeyPart<P1>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimaryKeyStorage {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1): StoredCacheEntryRef<S> =
        resolveStoredPrimaryEntryRef(name, storedAs, listOf(key.encodePart(p1)), listOf(key.name)) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(value: P1): StoredCachePartRef<S> =
        resolveStoredPrimaryPartRef(name, storedAs, key.encode(value), listOf(key.encodePart(value)), listOf(key.name)) as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimaryKey2<P1 : Any, P2 : Any, S>(
    val name: String,
    val key: KeyPartComposition2<P1, P2>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsValueView {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2): StoredCacheEntryRef<S> =
        resolveStoredPrimaryEntryRef(name, storedAs, key.encodeParts(p1, p2), key.partNames()) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(p1: P1, p2: P2): StoredCachePartRef<S> =
        resolveStoredPrimaryPartRef(name, storedAs, key.encode(p1, p2), key.encodeParts(p1, p2), key.partNames()) as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimaryKey3<P1 : Any, P2 : Any, P3 : Any, S>(
    val name: String,
    val key: KeyPartComposition3<P1, P2, P3>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsValueView {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3): StoredCacheEntryRef<S> =
        resolveStoredPrimaryEntryRef(name, storedAs, key.encodeParts(p1, p2, p3), key.partNames()) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(p1: P1, p2: P2, p3: P3): StoredCachePartRef<S> =
        resolveStoredPrimaryPartRef(name, storedAs, key.encode(p1, p2, p3), key.encodeParts(p1, p2, p3), key.partNames()) as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimaryKey4<P1 : Any, P2 : Any, P3 : Any, P4 : Any, S>(
    val name: String,
    val key: KeyPartComposition4<P1, P2, P3, P4>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsValueView {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3, p4: P4): StoredCacheEntryRef<S> =
        resolveStoredPrimaryEntryRef(name, storedAs, key.encodeParts(p1, p2, p3, p4), key.partNames()) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4): StoredCachePartRef<S> =
        resolveStoredPrimaryPartRef(name, storedAs, key.encode(p1, p2, p3, p4), key.encodeParts(p1, p2, p3, p4), key.partNames()) as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimaryKey5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S>(
    val name: String,
    val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsValueView {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): StoredCacheEntryRef<S> =
        resolveStoredPrimaryEntryRef(name, storedAs, key.encodeParts(p1, p2, p3, p4, p5), key.partNames()) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): StoredCachePartRef<S> =
        resolveStoredPrimaryPartRef(name, storedAs, key.encode(p1, p2, p3, p4, p5), key.encodeParts(p1, p2, p3, p4, p5), key.partNames()) as StoredCachePartRef<S>
}

@ExperimentalKacheableApi
data class TypedPrimaryKey6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S>(
    val name: String,
    val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsValueView {
    @Suppress("UNCHECKED_CAST")
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): StoredCacheEntryRef<S> =
        resolveStoredPrimaryEntryRef(name, storedAs, key.encodeParts(p1, p2, p3, p4, p5, p6), key.partNames()) as StoredCacheEntryRef<S>

    @Suppress("UNCHECKED_CAST")
    fun keyPart(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): StoredCachePartRef<S> =
        resolveStoredPrimaryPartRef(
            name,
            storedAs,
            key.encode(p1, p2, p3, p4, p5, p6),
            key.encodeParts(p1, p2, p3, p4, p5, p6),
            key.partNames(),
        ) as StoredCachePartRef<S>
}
