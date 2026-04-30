@file:OptIn(com.github.dave08.kacheable.ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.internal.keys

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.KeyPart

@PublishedApi
internal interface TypedPrimaryKeyDefinition {
    fun partNames(): List<String?>
}

@PublishedApi
internal interface TypedPrimarySecondaryKeyDefinition<P1 : Any> {
    val primary: KeyPart<P1>
    fun encodePrimaryParts(p1: P1): List<CacheArgs>
    fun primaryPartNames(): List<String?>
    fun secondaryPartNames(): List<String?>
    fun secondaryParts(): List<KeyPart<*>>
}
