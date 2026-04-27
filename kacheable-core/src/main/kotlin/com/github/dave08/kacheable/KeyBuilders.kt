@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

private fun <P1 : Any> secondaryKeyPart(
    vararg values: (P1) -> Any,
): KeyPart<P1> {
    require(values.isNotEmpty()) { "secondaryKeyPart requires at least one value extractor" }
    return SimpleSecondaryKeyPart(
        encoders = values.toList(),
        wildcardArgs = wildcardArgs(values.size),
    )
}

@ExperimentalKacheableApi
fun <P1 : Any> key(): KeyPart<P1> = secondaryKeyPart({ it })

@ExperimentalKacheableApi
fun <P1 : Any> key(
    vararg values: (P1) -> Any,
): KeyPart<P1> = secondaryKeyPart(*values)

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    mapper: KeyPart<P1> = key(),
    storedAs: CacheStorage.HashMap,
): HashMapMainKey<P1> = HashMapMainKey(label, SimpleMainKeyPart(label, mapper::encode))

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    mapper: KeyPart<P1> = key(),
    storedAs: CacheStorage.Set,
): SetMainKey<P1> = SetMainKey(label, SimpleMainKeyPart(label, mapper::encode))
