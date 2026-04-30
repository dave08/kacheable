@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.storage.resolveHashPrimarySecondaryPrimaryPartRef
import com.github.dave08.kacheable.internal.storage.resolveHashPrimarySecondarySelectedPartRef
import com.github.dave08.kacheable.internal.storage.resolveStoredPrimarySecondaryEntryRef
import com.github.dave08.kacheable.internal.storage.resolveStoredPrimarySecondaryPrimaryEntryRef
import com.github.dave08.kacheable.internal.storage.resolveStoredPrimarySecondaryPrimaryPartRef

@ExperimentalKacheableApi
data class TypedPrimarySecondaryKey2<P1 : Any, P2 : Any, S>(
    val name: String,
    val key: KeyPartPrimarySecondary2<P1, P2>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage {

    fun key(p1: P1): StoredCacheEntryRef<S> = resolveStoredPrimarySecondaryPrimaryEntryRef(
        name = name,
        storedAs = storedAs,
        primaryPartArgs = key.encodePrimaryParts(p1),
        primaryPartNames = key.primaryPartNames(),
    )

    fun key(p1: P1, p2: P2): StoredCacheEntryRef<S> = resolveStoredPrimarySecondaryEntryRef(
        name = name,
        storedAs = storedAs,
        primaryPartArgs = key.encodePrimaryParts(p1),
        primaryPartNames = key.primaryPartNames(),
        secondaryPartArgs = key.encodeSecondaryParts(p2),
        secondaryPartNames = key.secondaryPartNames(),
    )

    fun keyPart(value: P1): StoredCachePartRef<S> = resolveStoredPrimarySecondaryPrimaryPartRef(
        name = name,
        storedAs = storedAs,
        key = key,
        primaryValue = value,
    )
}

@ExperimentalKacheableApi
data class TypedPrimarySecondaryKey3<P1 : Any, P2 : Any, P3 : Any, S>(
    val name: String,
    val key: KeyPartPrimarySecondary3<P1, P2, P3>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage {

    fun key(p1: P1): StoredCacheEntryRef<S> = resolveStoredPrimarySecondaryPrimaryEntryRef(
        name = name,
        storedAs = storedAs,
        primaryPartArgs = key.encodePrimaryParts(p1),
        primaryPartNames = key.primaryPartNames(),
    )

    fun key(p1: P1, p2: P2, p3: P3): StoredCacheEntryRef<S> = resolveStoredPrimarySecondaryEntryRef(
        name = name,
        storedAs = storedAs,
        primaryPartArgs = key.encodePrimaryParts(p1),
        primaryPartNames = key.primaryPartNames(),
        secondaryPartArgs = key.encodeSecondaryParts(p2, p3),
        secondaryPartNames = key.secondaryPartNames(),
    )

    fun keyPart(value: P1): StoredCachePartRef<S> = resolveStoredPrimarySecondaryPrimaryPartRef(
        name = name,
        storedAs = storedAs,
        key = key,
        primaryValue = value,
    )
}

@ExperimentalKacheableApi
data class TypedPrimarySecondaryKey4<P1 : Any, P2 : Any, P3 : Any, P4 : Any, S>(
    val name: String,
    val key: KeyPartPrimarySecondary4<P1, P2, P3, P4>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage {

    fun key(p1: P1): StoredCacheEntryRef<S> = resolveStoredPrimarySecondaryPrimaryEntryRef(
        name = name,
        storedAs = storedAs,
        primaryPartArgs = key.encodePrimaryParts(p1),
        primaryPartNames = key.primaryPartNames(),
    )

    fun key(p1: P1, p2: P2, p3: P3, p4: P4): StoredCacheEntryRef<S> = resolveStoredPrimarySecondaryEntryRef(
        name = name,
        storedAs = storedAs,
        primaryPartArgs = key.encodePrimaryParts(p1),
        primaryPartNames = key.primaryPartNames(),
        secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4),
        secondaryPartNames = key.secondaryPartNames(),
    )

    fun keyPart(value: P1): StoredCachePartRef<S> = resolveStoredPrimarySecondaryPrimaryPartRef(
        name = name,
        storedAs = storedAs,
        key = key,
        primaryValue = value,
    )
}

@ExperimentalKacheableApi
data class TypedPrimarySecondaryKey5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S>(
    val name: String,
    val key: KeyPartPrimarySecondary5<P1, P2, P3, P4, P5>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage {

    fun key(p1: P1): StoredCacheEntryRef<S> = resolveStoredPrimarySecondaryPrimaryEntryRef(
        name = name,
        storedAs = storedAs,
        primaryPartArgs = key.encodePrimaryParts(p1),
        primaryPartNames = key.primaryPartNames(),
    )

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): StoredCacheEntryRef<S> = resolveStoredPrimarySecondaryEntryRef(
        name = name,
        storedAs = storedAs,
        primaryPartArgs = key.encodePrimaryParts(p1),
        primaryPartNames = key.primaryPartNames(),
        secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4, p5),
        secondaryPartNames = key.secondaryPartNames(),
    )

    fun keyPart(value: P1): StoredCachePartRef<S> = resolveStoredPrimarySecondaryPrimaryPartRef(
        name = name,
        storedAs = storedAs,
        key = key,
        primaryValue = value,
    )
}

@ExperimentalKacheableApi
data class TypedPrimarySecondaryKey6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S>(
    val name: String,
    val key: KeyPartPrimarySecondary6<P1, P2, P3, P4, P5, P6>,
    val storedAs: S,
) where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage {

    fun key(p1: P1): StoredCacheEntryRef<S> = resolveStoredPrimarySecondaryPrimaryEntryRef(
        name = name,
        storedAs = storedAs,
        primaryPartArgs = key.encodePrimaryParts(p1),
        primaryPartNames = key.primaryPartNames(),
    )

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): StoredCacheEntryRef<S> = resolveStoredPrimarySecondaryEntryRef(
        name = name,
        storedAs = storedAs,
        primaryPartArgs = key.encodePrimaryParts(p1),
        primaryPartNames = key.primaryPartNames(),
        secondaryPartArgs = key.encodeSecondaryParts(p2, p3, p4, p5, p6),
        secondaryPartNames = key.secondaryPartNames(),
    )

    fun keyPart(value: P1): StoredCachePartRef<S> = resolveStoredPrimarySecondaryPrimaryPartRef(
        name = name,
        storedAs = storedAs,
        key = key,
        primaryValue = value,
    )
}

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, S> TypedPrimarySecondaryKey2<P1, P2, S>.keyPart(
    value: P1,
    vararg secondaryParts: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    resolveHashPrimarySecondaryPrimaryPartRef(name, key, value, secondaryParts)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, S> TypedPrimarySecondaryKey2<P1, P2, S>.keyPart(
    vararg selections: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    resolveHashPrimarySecondarySelectedPartRef<P1>(name, key, selections)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, S> TypedPrimarySecondaryKey3<P1, P2, P3, S>.keyPart(
    value: P1,
    vararg secondaryParts: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    resolveHashPrimarySecondaryPrimaryPartRef(name, key, value, secondaryParts)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, S> TypedPrimarySecondaryKey3<P1, P2, P3, S>.keyPart(
    vararg selections: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    resolveHashPrimarySecondarySelectedPartRef<P1>(name, key, selections)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, S> TypedPrimarySecondaryKey4<P1, P2, P3, P4, S>.keyPart(
    value: P1,
    vararg secondaryParts: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    resolveHashPrimarySecondaryPrimaryPartRef(name, key, value, secondaryParts)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, S> TypedPrimarySecondaryKey4<P1, P2, P3, P4, S>.keyPart(
    vararg selections: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    resolveHashPrimarySecondarySelectedPartRef<P1>(name, key, selections)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S> TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, S>.keyPart(
    value: P1,
    vararg secondaryParts: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    resolveHashPrimarySecondaryPrimaryPartRef(name, key, value, secondaryParts)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S> TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, S>.keyPart(
    vararg selections: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    resolveHashPrimarySecondarySelectedPartRef<P1>(name, key, selections)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S> TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, S>.keyPart(
    value: P1,
    vararg secondaryParts: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    resolveHashPrimarySecondaryPrimaryPartRef(name, key, value, secondaryParts)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S> TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, S>.keyPart(
    vararg selections: KeyPartValue,
): CachePartRef<CacheStorage.HashMap> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    resolveHashPrimarySecondarySelectedPartRef<P1>(name, key, selections)
