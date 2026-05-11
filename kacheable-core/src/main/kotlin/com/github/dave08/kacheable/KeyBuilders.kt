@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

private fun <P1> mappedKeyPart(
    name: String? = null,
    values: List<(P1) -> Any?>,
): KeyPart<P1> {
    require(values.isNotEmpty()) { "keyPart requires at least one value extractor" }
    return SimpleKeyPart(
        name = name,
        encoders = values,
    )
}

@ExperimentalKacheableApi
fun <P1> keyPart(): KeyPart<P1> = mappedKeyPart(values = listOf({ it }))

@ExperimentalKacheableApi
fun <P1> keyPart(
    vararg values: (P1) -> Any?,
): KeyPart<P1> = mappedKeyPart(values = values.toList())

@ExperimentalKacheableApi
fun <P1> keyPart(
    name: String,
): KeyPart<P1> = mappedKeyPart(name = name, values = listOf({ it }))

@ExperimentalKacheableApi
fun <P1> keyPart(
    name: String,
    vararg values: (P1) -> Any?,
): KeyPart<P1> = mappedKeyPart(name = name, values = values.toList())

@ExperimentalKacheableApi
fun rawKeyPart(): KeyPart<CacheArgs> = RawKeyPart
