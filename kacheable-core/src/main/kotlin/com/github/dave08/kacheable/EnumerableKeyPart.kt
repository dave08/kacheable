package com.github.dave08.kacheable

import com.github.dave08.kacheable.store.CacheCodec
import com.github.dave08.kacheable.store.cacheCodec
import kotlinx.serialization.serializer
import kotlin.reflect.KProperty

/**
 * An inner key whose original typed value can be retained for partition enumeration.
 * [codec] encodes separate metadata; physical key segments still come from [encode].
 */
interface EnumerableKeyPart<K> : KeyPart<K> {
    val codec: CacheCodec<K>

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): EnumerableKeyPart<K> =
        if (name != null) this else NamedEnumerableKeyPart(property.name, this)

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): EnumerableKeyPart<K> = this
}

/** Uses the existing serialization codec to retain the logical key independently of its segments. */
inline fun <reified K> enumerableKeyPart(
    name: String? = null,
    vararg values: (K) -> Any?,
): EnumerableKeyPart<K> = enumerableKeyPart(name, cacheCodec(serializer<K>()), *values)

/** Creates an enumerable key with an explicitly supplied codec, including custom representations. */
fun <K> enumerableKeyPart(
    name: String? = null,
    codec: CacheCodec<K>,
    vararg values: (K) -> Any?,
): EnumerableKeyPart<K> {
    val delegate = SimpleKeyPart(name, values.toList().ifEmpty { listOf({ value: K -> value }) })
    return object : EnumerableKeyPart<K>, KeyPart<K> by delegate {
        override val codec: CacheCodec<K> = codec
        override fun provideDelegate(thisRef: Any?, property: KProperty<*>): EnumerableKeyPart<K> =
            if (name != null) this else NamedEnumerableKeyPart(property.name, this)
        override fun getValue(thisRef: Any?, property: KProperty<*>): EnumerableKeyPart<K> = this
    }
}

private class NamedEnumerableKeyPart<K>(
    override val name: String,
    private val delegate: EnumerableKeyPart<K>,
) : EnumerableKeyPart<K> {
    override val codec: CacheCodec<K> get() = delegate.codec
    override val segmentCount: Int? get() = delegate.segmentCount
    override fun encode(value: K): CacheArgs = delegate.encode(value)
}
