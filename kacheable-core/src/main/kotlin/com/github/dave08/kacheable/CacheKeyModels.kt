@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.keys.validateUniqueKeyPartNames
import kotlin.reflect.KProperty

@ExperimentalKacheableApi
data class PrimarySecondaryCacheArgs(
    val primary: CacheArgs,
    val secondary: CacheArgs? = null,
    val primaryPartArgs: List<CacheArgs> = listOf(primary),
    val primaryPartNames: List<String?> = List(primaryPartArgs.size) { null },
    val secondaryPartArgs: List<CacheArgs> = secondary?.let(::listOf) ?: emptyList(),
    val secondaryPartNames: List<String?> = List(secondaryPartArgs.size) { null },
) {
    init {
        require(primaryPartArgs.size == primaryPartNames.size) { "Primary key-part args and names must align." }
        require(secondaryPartArgs.size == secondaryPartNames.size) { "Secondary key-part args and names must align." }

        validateUniqueKeyPartNames(primaryPartNames + secondaryPartNames)
    }

    val flattened: CacheArgs = joinArgs(*(primaryPartArgs + secondaryPartArgs).toTypedArray())
}

@ExperimentalKacheableApi
interface CacheEntryPartRef {
    val name: String
    val args: CacheArgs
    val storage: CacheStorage
    val secondaryPatternPartArgs: List<CacheArgs>?
        get() = null
    val cacheArgs: PrimarySecondaryCacheArgs
        get() = PrimarySecondaryCacheArgs(args)
}

@ExperimentalKacheableApi
interface KeyPart<P1> {
    val name: String?
    val segmentCount: Int?
    fun encode(value: P1): CacheArgs

    operator fun invoke(value: P1): KeyPartValue = KeyPartValue(this, encode(value))
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): KeyPart<P1> = withName(property.name)
    operator fun getValue(thisRef: Any?, property: KProperty<*>): KeyPart<P1> = this
}

@ExperimentalKacheableApi
open class KeyPartValue(
    open val keyPart: KeyPart<*>,
    open val args: CacheArgs,
) {
    val name: String?
        get() = keyPart.name
}

@PublishedApi
internal data class SimpleKeyPart<P1>(
    override val name: String? = null,
    private val encoders: List<(P1) -> Any?>,
) : KeyPart<P1> {
    override val segmentCount: Int = encoders.size
    override fun encode(value: P1): CacheArgs = argsOf(*encoders.map { it(value) }.toTypedArray())
}

@PublishedApi
internal data object RawKeyPart : KeyPart<CacheArgs> {
    override val name: String? = null
    override val segmentCount: Int? = null
    override fun encode(value: CacheArgs): CacheArgs = value
}

@PublishedApi
internal data object CachePatternWildcard {
    override fun toString(): String = "*"
}

@PublishedApi
internal data class NamedKeyPart<P1>(
    override val name: String,
    private val delegate: KeyPart<P1>,
) : KeyPart<P1> {
    override val segmentCount: Int? = delegate.segmentCount
    override fun encode(value: P1): CacheArgs = delegate.encode(value)
}

@PublishedApi
internal fun <P1> KeyPart<P1>.encodePart(value: P1): CacheArgs = encode(value)

@PublishedApi
internal fun <P1> KeyPart<P1>.withName(name: String): KeyPart<P1> =
    when {
        this.name == name -> this
        this.name != null -> this
        else -> NamedKeyPart(name, this)
    }
