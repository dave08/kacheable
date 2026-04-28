@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

private fun <P1 : Any> mappedKeyPart(
    vararg values: (P1) -> Any,
): KeyPart<P1> {
    require(values.isNotEmpty()) { "keyPart requires at least one value extractor" }
    return SimpleSecondaryKeyPart(
        encoders = values.toList(),
        wildcardArgs = wildcardArgs(values.size),
    )
}

@ExperimentalKacheableApi
fun <P1 : Any> keyPart(): KeyPart<P1> = mappedKeyPart({ it })

@ExperimentalKacheableApi
fun <P1 : Any> keyPart(
    vararg values: (P1) -> Any,
): KeyPart<P1> = mappedKeyPart(*values)

@ExperimentalKacheableApi
fun rawKeyPart(segmentCount: Int): KeyPart<CacheArgs> {
    require(segmentCount >= 0) { "segmentCount must be non-negative" }
    return RawKeyPart(wildcardArgs = wildcardArgs(segmentCount))
}

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    mapper: KeyPart<P1>,
    storedAs: CacheStorage.Set,
): SetPrimaryKey<P1> = SetPrimaryKey(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    mapper: KeyPart<P1>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey<P1> = HashMapPrimaryKey(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey<P1> = mainKey(label, keyPart(), storedAs)

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    storedAs: CacheStorage.Set,
): SetPrimaryKey<P1> = mainKey(label, keyPart(), storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any> mainKey(
    label: String,
    mapper: KeyPartComposition2<P1, P2>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey2<P1, P2> = HashMapPrimaryKey2(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any> mainKey(
    label: String,
    mapper: KeyPartComposition3<P1, P2, P3>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey3<P1, P2, P3> = HashMapPrimaryKey3(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> mainKey(
    label: String,
    mapper: KeyPartComposition4<P1, P2, P3, P4>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey4<P1, P2, P3, P4> = HashMapPrimaryKey4(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> mainKey(
    label: String,
    mapper: KeyPartComposition5<P1, P2, P3, P4, P5>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey5<P1, P2, P3, P4, P5> = HashMapPrimaryKey5(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> mainKey(
    label: String,
    mapper: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey6<P1, P2, P3, P4, P5, P6> = HashMapPrimaryKey6(label, mapper)
