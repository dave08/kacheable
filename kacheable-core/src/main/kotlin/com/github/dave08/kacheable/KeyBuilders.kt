@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.keys.typedPrimaryEntryKey
import com.github.dave08.kacheable.internal.keys.typedPrimarySecondaryEntryKey

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
): TypedPrimaryKey<P1, CacheStorage.String> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    mapper: KeyPart<P1>,
    storedAs: CacheStorage.Set,
): TypedPrimaryKey<P1, CacheStorage.Set> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    mapper: KeyPart<P1>,
    storedAs: CacheStorage.HashMap,
): TypedPrimaryKey<P1, CacheStorage.HashMap> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    storedAs: CacheStorage.String,
): TypedPrimaryKey<P1, CacheStorage.String> = entryKey(label, keyPart(), storedAs)

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    storedAs: CacheStorage.HashMap,
): TypedPrimaryKey<P1, CacheStorage.HashMap> = entryKey(label, keyPart(), storedAs)

@ExperimentalKacheableApi
fun <P1 : Any> entryKey(
    label: String,
    storedAs: CacheStorage.Set,
): TypedPrimaryKey<P1, CacheStorage.Set> = entryKey(label, keyPart(), storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition2<P1, P2>,
    storedAs: CacheStorage.String,
): TypedPrimaryKey2<P1, P2, CacheStorage.String> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition2<P1, P2>,
    storedAs: CacheStorage.HashMap,
): TypedPrimaryKey2<P1, P2, CacheStorage.HashMap> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any> entryKey(
    label: String,
    mapper: KeyPartPrimarySecondary2<P1, P2>,
    storedAs: CacheStorage.HashMap,
): TypedPrimarySecondaryKey2<P1, P2, CacheStorage.HashMap> = typedPrimarySecondaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any> entryKey(
    label: String,
    mapper: KeyPartPrimarySecondary2<P1, P2>,
    storedAs: CacheStorage.Set,
): TypedPrimarySecondaryKey2<P1, P2, CacheStorage.Set> = typedPrimarySecondaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition3<P1, P2, P3>,
    storedAs: CacheStorage.String,
): TypedPrimaryKey3<P1, P2, P3, CacheStorage.String> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition3<P1, P2, P3>,
    storedAs: CacheStorage.HashMap,
): TypedPrimaryKey3<P1, P2, P3, CacheStorage.HashMap> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any> entryKey(
    label: String,
    mapper: KeyPartPrimarySecondary3<P1, P2, P3>,
    storedAs: CacheStorage.HashMap,
): TypedPrimarySecondaryKey3<P1, P2, P3, CacheStorage.HashMap> = typedPrimarySecondaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any> entryKey(
    label: String,
    mapper: KeyPartPrimarySecondary3<P1, P2, P3>,
    storedAs: CacheStorage.Set,
): TypedPrimarySecondaryKey3<P1, P2, P3, CacheStorage.Set> = typedPrimarySecondaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition4<P1, P2, P3, P4>,
    storedAs: CacheStorage.String,
): TypedPrimaryKey4<P1, P2, P3, P4, CacheStorage.String> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition4<P1, P2, P3, P4>,
    storedAs: CacheStorage.HashMap,
): TypedPrimaryKey4<P1, P2, P3, P4, CacheStorage.HashMap> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> entryKey(
    label: String,
    mapper: KeyPartPrimarySecondary4<P1, P2, P3, P4>,
    storedAs: CacheStorage.HashMap,
): TypedPrimarySecondaryKey4<P1, P2, P3, P4, CacheStorage.HashMap> = typedPrimarySecondaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> entryKey(
    label: String,
    mapper: KeyPartPrimarySecondary4<P1, P2, P3, P4>,
    storedAs: CacheStorage.Set,
): TypedPrimarySecondaryKey4<P1, P2, P3, P4, CacheStorage.Set> = typedPrimarySecondaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition5<P1, P2, P3, P4, P5>,
    storedAs: CacheStorage.String,
): TypedPrimaryKey5<P1, P2, P3, P4, P5, CacheStorage.String> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition5<P1, P2, P3, P4, P5>,
    storedAs: CacheStorage.HashMap,
): TypedPrimaryKey5<P1, P2, P3, P4, P5, CacheStorage.HashMap> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> entryKey(
    label: String,
    mapper: KeyPartPrimarySecondary5<P1, P2, P3, P4, P5>,
    storedAs: CacheStorage.HashMap,
): TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, CacheStorage.HashMap> = typedPrimarySecondaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> entryKey(
    label: String,
    mapper: KeyPartPrimarySecondary5<P1, P2, P3, P4, P5>,
    storedAs: CacheStorage.Set,
): TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, CacheStorage.Set> = typedPrimarySecondaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    storedAs: CacheStorage.String,
): TypedPrimaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.String> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> entryKey(
    label: String,
    mapper: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    storedAs: CacheStorage.HashMap,
): TypedPrimaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.HashMap> = typedPrimaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> entryKey(
    label: String,
    mapper: KeyPartPrimarySecondary6<P1, P2, P3, P4, P5, P6>,
    storedAs: CacheStorage.HashMap,
): TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.HashMap> = typedPrimarySecondaryEntryKey(label, mapper, storedAs)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> entryKey(
    label: String,
    mapper: KeyPartPrimarySecondary6<P1, P2, P3, P4, P5, P6>,
    storedAs: CacheStorage.Set,
): TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, CacheStorage.Set> = typedPrimarySecondaryEntryKey(label, mapper, storedAs)
