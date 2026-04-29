@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

private fun <P1 : Any> mappedKeyPart(
    name: String? = null,
    values: List<(P1) -> Any>,
): KeyPart<P1> {
    require(values.isNotEmpty()) { "keyPart requires at least one value extractor" }
    return SimpleSecondaryKeyPart(
        name = name,
        encoders = values,
    )
}

@ExperimentalKacheableApi
fun <P1 : Any> keyPart(): KeyPart<P1> = mappedKeyPart(values = listOf({ it }))

@ExperimentalKacheableApi
fun <P1 : Any> keyPart(
    vararg values: (P1) -> Any,
): KeyPart<P1> = mappedKeyPart(values = values.toList())

@ExperimentalKacheableApi
fun <P1 : Any> keyPart(
    name: String,
): KeyPart<P1> = mappedKeyPart(name = name, values = listOf({ it }))

@ExperimentalKacheableApi
fun <P1 : Any> keyPart(
    name: String,
    vararg values: (P1) -> Any,
): KeyPart<P1> = mappedKeyPart(name = name, values = values.toList())

@ExperimentalKacheableApi
fun rawKeyPart(): KeyPart<CacheArgs> = RawKeyPart

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    mapper: KeyPart<P1>,
    storedAs: CacheStorage.String,
): StringPrimaryKey<P1> = StringPrimaryKey(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    mapper: KeyPart<P1>,
    storedAs: CacheStorage.Set,
): SetPrimaryKey<P1> = SetPrimaryKey(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    mapper: KeyPart<P1>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey<P1> = HashMapPrimaryKey(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    storedAs: CacheStorage.String,
): StringPrimaryKey<P1> = entryKey(label, keyPart(), storedAs)

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey<P1> = entryKey(label, keyPart(), storedAs)

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    storedAs: CacheStorage.Set,
): SetPrimaryKey<P1> = entryKey(label, keyPart(), storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition2<P1, P2>,
    storedAs: CacheStorage.String,
): StringPrimaryKey2<P1, P2> = StringPrimaryKey2(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition2<P1, P2>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey2<P1, P2> = HashMapPrimaryKey2(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any> entryKey(
    label: String,
    mapper: KeyPartCompositionGroup2<P1, P2>,
    storedAs: CacheStorage.HashMap,
): HashMapStoredCache2<P1, P2> = HashMapStoredCache2(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any> entryKey(
    label: String,
    mapper: KeyPartCompositionGroup2<P1, P2>,
    storedAs: CacheStorage.Set,
): SetStoredCache2<P1, P2> = SetStoredCache2(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition3<P1, P2, P3>,
    storedAs: CacheStorage.String,
): StringPrimaryKey3<P1, P2, P3> = StringPrimaryKey3(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition3<P1, P2, P3>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey3<P1, P2, P3> = HashMapPrimaryKey3(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any> entryKey(
    label: String,
    mapper: KeyPartCompositionGroup3<P1, P2, P3>,
    storedAs: CacheStorage.HashMap,
): HashMapStoredCache3<P1, P2, P3> = HashMapStoredCache3(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition4<P1, P2, P3, P4>,
    storedAs: CacheStorage.String,
): StringPrimaryKey4<P1, P2, P3, P4> = StringPrimaryKey4(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition4<P1, P2, P3, P4>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey4<P1, P2, P3, P4> = HashMapPrimaryKey4(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> entryKey(
    label: String,
    mapper: KeyPartCompositionGroup4<P1, P2, P3, P4>,
    storedAs: CacheStorage.HashMap,
): HashMapStoredCache4<P1, P2, P3, P4> = HashMapStoredCache4(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition5<P1, P2, P3, P4, P5>,
    storedAs: CacheStorage.String,
): StringPrimaryKey5<P1, P2, P3, P4, P5> = StringPrimaryKey5(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition5<P1, P2, P3, P4, P5>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey5<P1, P2, P3, P4, P5> = HashMapPrimaryKey5(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> entryKey(
    label: String,
    mapper: KeyPartCompositionGroup5<P1, P2, P3, P4, P5>,
    storedAs: CacheStorage.HashMap,
): HashMapStoredCache5<P1, P2, P3, P4, P5> = HashMapStoredCache5(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    storedAs: CacheStorage.String,
): StringPrimaryKey6<P1, P2, P3, P4, P5, P6> = StringPrimaryKey6(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    storedAs: CacheStorage.HashMap,
): HashMapPrimaryKey6<P1, P2, P3, P4, P5, P6> = HashMapPrimaryKey6(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> entryKey(
    label: String,
    mapper: KeyPartCompositionGroup6<P1, P2, P3, P4, P5, P6>,
    storedAs: CacheStorage.HashMap,
): HashMapStoredCache6<P1, P2, P3, P4, P5, P6> = HashMapStoredCache6(label, mapper)
