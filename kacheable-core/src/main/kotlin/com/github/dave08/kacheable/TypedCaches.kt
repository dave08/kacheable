@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.blocking.BlockingKacheable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

@ExperimentalKacheableApi
sealed interface CacheStorageLayout {
    data object StringValue : CacheStorageLayout
    data object HashValue : CacheStorageLayout
}

@ExperimentalKacheableApi
sealed interface CacheArgs {
    fun toParamsArray(): Array<out Any>
}

@ExperimentalKacheableApi
data object CacheWildcard {
    override fun toString(): String = "*"
}

@ExperimentalKacheableApi
data object CacheArgs0 : CacheArgs {
    override fun toParamsArray(): Array<out Any> = emptyArray()
}

@ExperimentalKacheableApi
class CachePatternArgs(
    private vararg val params: Any,
) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = params
}

@ExperimentalKacheableApi
fun interface CacheArgsEncoder<T> {
    fun encode(value: T): CacheArgs
}

@ExperimentalKacheableApi
fun <T> cacheArgsEncoder(block: (T) -> CacheArgs): CacheArgsEncoder<T> = CacheArgsEncoder(block)

@ExperimentalKacheableApi
fun argsOf(vararg params: Any): CacheArgs = CachePatternArgs(*params)

@ExperimentalKacheableApi
fun patternArgs(vararg params: Any): CacheArgs = CachePatternArgs(*params)

@ExperimentalKacheableApi
data class CacheArgs1<P1 : Any>(val p1: P1) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1)
}

@ExperimentalKacheableApi
data class CacheArgs2<P1 : Any, P2 : Any>(val p1: P1, val p2: P2) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1, p2)
}

@ExperimentalKacheableApi
data class CacheArgs3<P1 : Any, P2 : Any, P3 : Any>(val p1: P1, val p2: P2, val p3: P3) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1, p2, p3)
}

@ExperimentalKacheableApi
data class CacheArgs4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1, p2, p3, p4)
}

@ExperimentalKacheableApi
data class CacheArgs5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
    val p5: P5,
) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1, p2, p3, p4, p5)
}

@ExperimentalKacheableApi
data class CacheArgs6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
    val p5: P5,
    val p6: P6,
) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1, p2, p3, p4, p5, p6)
}

@ExperimentalKacheableApi
interface CacheDefinition<R> {
    val name: String
    val serializer: KSerializer<R>
    val storageLayout: CacheStorageLayout
}

@ExperimentalKacheableApi
interface CacheCall<R> {
    val definition: CacheDefinition<R>
    val args: CacheArgs
}

@ExperimentalKacheableApi
interface CacheGroup {
    val name: String
    val args: CacheArgs
}

@ExperimentalKacheableApi
interface Cache0<R> : CacheDefinition<R> {
    operator fun invoke(): CacheCall<R>
}

@ExperimentalKacheableApi
interface Cache1<P1 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1): CacheCall<R>
}

@ExperimentalKacheableApi
interface Cache2<P1 : Any, P2 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2): CacheCall<R>
}

@ExperimentalKacheableApi
interface Cache3<P1 : Any, P2 : Any, P3 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3): CacheCall<R>
}

@ExperimentalKacheableApi
interface Cache4<P1 : Any, P2 : Any, P3 : Any, P4 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): CacheCall<R>
}

@ExperimentalKacheableApi
interface Cache5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheCall<R>
}

@ExperimentalKacheableApi
interface Cache6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheCall<R>
}

@ExperimentalKacheableApi
interface KeyedCache<K : Any, R> : CacheDefinition<R> {
    operator fun invoke(key: K): CacheCall<R>
}

@ExperimentalKacheableApi
interface GroupedCache0<R, G : Any> : Cache0<R> {
    fun group(key: G): CacheGroup
}

@ExperimentalKacheableApi
interface GroupedCache1<P1 : Any, R, G : Any> : Cache1<P1, R> {
    fun group(key: G): CacheGroup
}

@ExperimentalKacheableApi
interface GroupedCache2<P1 : Any, P2 : Any, R, G : Any> : Cache2<P1, P2, R> {
    fun group(key: G): CacheGroup
}

@ExperimentalKacheableApi
interface GroupedCache3<P1 : Any, P2 : Any, P3 : Any, R, G : Any> : Cache3<P1, P2, P3, R> {
    fun group(key: G): CacheGroup
}

@ExperimentalKacheableApi
interface GroupedCache4<P1 : Any, P2 : Any, P3 : Any, P4 : Any, R, G : Any> : Cache4<P1, P2, P3, P4, R> {
    fun group(key: G): CacheGroup
}

@ExperimentalKacheableApi
interface GroupedCache5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, R, G : Any> : Cache5<P1, P2, P3, P4, P5, R> {
    fun group(key: G): CacheGroup
}

@ExperimentalKacheableApi
interface GroupedCache6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, R, G : Any> : Cache6<P1, P2, P3, P4, P5, P6, R> {
    fun group(key: G): CacheGroup
}

@ExperimentalKacheableApi
interface MainKeyPart<P1 : Any> : CacheArgsEncoder<P1> {
    val label: String
}

@ExperimentalKacheableApi
interface SecondaryKeyPart<P1 : Any> : CacheArgsEncoder<P1> {
    val wildcardArgs: CacheArgs
}

@ExperimentalKacheableApi
typealias MainKey<P1> = MainKeyPart<P1>

@ExperimentalKacheableApi
typealias KeyMapper<P1> = SecondaryKeyPart<P1>

@ExperimentalKacheableApi
data class SecondaryKeyComposition2<P1 : Any, P2 : Any>(
    val first: SecondaryKeyPart<P1>,
    val second: SecondaryKeyPart<P2>,
) {
    fun encode(p1: P1, p2: P2): CacheArgs = joinArgs(first.encode(p1), second.encode(p2))
    val wildcardArgs: CacheArgs = joinArgs(first.wildcardArgs, second.wildcardArgs)
}

@ExperimentalKacheableApi
data class SecondaryKeyComposition3<P1 : Any, P2 : Any, P3 : Any>(
    val first: SecondaryKeyPart<P1>,
    val second: SecondaryKeyPart<P2>,
    val third: SecondaryKeyPart<P3>,
) {
    fun encode(p1: P1, p2: P2, p3: P3): CacheArgs = joinArgs(first.encode(p1), second.encode(p2), third.encode(p3))
    val wildcardArgs: CacheArgs = joinArgs(first.wildcardArgs, second.wildcardArgs, third.wildcardArgs)
}

@ExperimentalKacheableApi
data class SecondaryKeyComposition4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val first: SecondaryKeyPart<P1>,
    val second: SecondaryKeyPart<P2>,
    val third: SecondaryKeyPart<P3>,
    val fourth: SecondaryKeyPart<P4>,
) {
    fun encode(p1: P1, p2: P2, p3: P3, p4: P4): CacheArgs =
        joinArgs(first.encode(p1), second.encode(p2), third.encode(p3), fourth.encode(p4))

    val wildcardArgs: CacheArgs =
        joinArgs(first.wildcardArgs, second.wildcardArgs, third.wildcardArgs, fourth.wildcardArgs)
}

@ExperimentalKacheableApi
data class SecondaryKeyComposition5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val first: SecondaryKeyPart<P1>,
    val second: SecondaryKeyPart<P2>,
    val third: SecondaryKeyPart<P3>,
    val fourth: SecondaryKeyPart<P4>,
    val fifth: SecondaryKeyPart<P5>,
) {
    fun encode(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheArgs =
        joinArgs(first.encode(p1), second.encode(p2), third.encode(p3), fourth.encode(p4), fifth.encode(p5))

    val wildcardArgs: CacheArgs =
        joinArgs(first.wildcardArgs, second.wildcardArgs, third.wildcardArgs, fourth.wildcardArgs, fifth.wildcardArgs)
}

@ExperimentalKacheableApi
data class MainSecondaryKey2<P1 : Any, P2 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: SecondaryKeyPart<P2>,
)

@ExperimentalKacheableApi
data class MainSecondaryKey3<P1 : Any, P2 : Any, P3 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: SecondaryKeyComposition2<P2, P3>,
)

@ExperimentalKacheableApi
data class MainSecondaryKey4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: SecondaryKeyComposition3<P2, P3, P4>,
)

@ExperimentalKacheableApi
data class MainSecondaryKey5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: SecondaryKeyComposition4<P2, P3, P4, P5>,
)

@ExperimentalKacheableApi
data class MainSecondaryKey6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: SecondaryKeyComposition5<P2, P3, P4, P5, P6>,
)

@PublishedApi
internal data class SimpleCacheDefinition<R>(
    override val name: String,
    override val serializer: KSerializer<R>,
    override val storageLayout: CacheStorageLayout,
) : CacheDefinition<R>

@PublishedApi
internal data class SimpleCacheCall<R>(
    override val definition: CacheDefinition<R>,
    override val args: CacheArgs,
) : CacheCall<R>

@PublishedApi
internal data class SimpleCacheGroup(
    override val name: String,
    override val args: CacheArgs,
) : CacheGroup

@PublishedApi
internal data class SimpleMainKeyPart<P1 : Any>(
    override val label: String,
    private val encoder: CacheArgsEncoder<P1>,
) : MainKeyPart<P1> {
    override fun encode(value: P1): CacheArgs = encoder.encode(value)
}

@PublishedApi
internal data class SimpleSecondaryKeyPart<P1 : Any>(
    private val encoder: CacheArgsEncoder<P1>,
    override val wildcardArgs: CacheArgs,
) : SecondaryKeyPart<P1> {
    override fun encode(value: P1): CacheArgs = encoder.encode(value)
}

@PublishedApi
internal fun joinArgs(vararg segments: CacheArgs): CacheArgs {
    val params = segments.flatMap { it.toParamsArray().toList() }
    return CachePatternArgs(*params.toTypedArray())
}

@PublishedApi
internal fun wildcardArgs(size: Int): CacheArgs = CachePatternArgs(*Array(size) { CacheWildcard as Any })

@ExperimentalKacheableApi
fun <P1 : Any> mainKeyPart(
    label: String,
    vararg values: (P1) -> Any,
): MainKeyPart<P1> {
    require(values.isNotEmpty()) { "mainKeyPart requires at least one value extractor" }
    return SimpleMainKeyPart(
        label = label,
        encoder = cacheArgsEncoder { value -> argsOf(*values.map { extractor -> extractor(value) }.toTypedArray()) },
    )
}

@ExperimentalKacheableApi
fun <P1 : Any> secondaryKeyPart(
    vararg values: (P1) -> Any,
): SecondaryKeyPart<P1> {
    require(values.isNotEmpty()) { "secondaryKeyPart requires at least one value extractor" }
    return SimpleSecondaryKeyPart(
        encoder = cacheArgsEncoder { value -> argsOf(*values.map { extractor -> extractor(value) }.toTypedArray()) },
        wildcardArgs = wildcardArgs(values.size),
    )
}

@ExperimentalKacheableApi
fun <P1 : Any> keyMapper(): KeyMapper<P1> = secondaryKeyPart({ it })

@ExperimentalKacheableApi
fun <P1 : Any> keyMapper(
    vararg values: (P1) -> Any,
): KeyMapper<P1> = secondaryKeyPart(*values)

@ExperimentalKacheableApi
fun <P1 : Any> key(): KeyMapper<P1> = keyMapper()

@ExperimentalKacheableApi
fun <P1 : Any> key(
    vararg values: (P1) -> Any,
): KeyMapper<P1> = keyMapper(*values)

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    mapper: KeyMapper<P1> = keyMapper(),
): MainKey<P1> = SimpleMainKeyPart(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    vararg values: (P1) -> Any,
): MainKey<P1> = mainKey(label, keyMapper(*values))

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyPart<P2>,
): MainSecondaryKey2<P1, P2> = MainSecondaryKey2(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> SecondaryKeyPart<P1>.plus(
    other: SecondaryKeyPart<P2>,
): SecondaryKeyComposition2<P1, P2> = SecondaryKeyComposition2(this, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> SecondaryKeyComposition2<P1, P2>.plus(
    other: SecondaryKeyPart<P3>,
): SecondaryKeyComposition3<P1, P2, P3> = SecondaryKeyComposition3(first, second, other)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any> composite(parts: SecondaryKeyComposition2<P1, P2>): SecondaryKeyComposition2<P1, P2> = parts

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any> composite(parts: SecondaryKeyComposition3<P1, P2, P3>): SecondaryKeyComposition3<P1, P2, P3> = parts

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> composite(
    parts: SecondaryKeyComposition4<P1, P2, P3, P4>,
): SecondaryKeyComposition4<P1, P2, P3, P4> = parts

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> composite(
    parts: SecondaryKeyComposition5<P1, P2, P3, P4, P5>,
): SecondaryKeyComposition5<P1, P2, P3, P4, P5> = parts

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition2<P2, P3>,
): MainSecondaryKey3<P1, P2, P3> = MainSecondaryKey3(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> SecondaryKeyComposition3<P1, P2, P3>.plus(
    other: SecondaryKeyPart<P4>,
): SecondaryKeyComposition4<P1, P2, P3, P4> = SecondaryKeyComposition4(first, second, third, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition3<P2, P3, P4>,
): MainSecondaryKey4<P1, P2, P3, P4> = MainSecondaryKey4(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> SecondaryKeyComposition4<P1, P2, P3, P4>.plus(
    other: SecondaryKeyPart<P5>,
): SecondaryKeyComposition5<P1, P2, P3, P4, P5> = SecondaryKeyComposition5(first, second, third, fourth, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition4<P2, P3, P4, P5>,
): MainSecondaryKey5<P1, P2, P3, P4, P5> = MainSecondaryKey5(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> SecondaryKeyComposition5<P1, P2, P3, P4, P5>.plus(
    other: SecondaryKeyPart<P6>,
): Nothing = throw UnsupportedOperationException("Secondary key composition already supports up to 5 parameters")

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition5<P2, P3, P4, P5, P6>,
): MainSecondaryKey6<P1, P2, P3, P4, P5, P6> = MainSecondaryKey6(this, secondary)


@ExperimentalKacheableApi
inline fun <reified R> cache0(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache0<R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache0<R>, CacheDefinition<R> by definition {
        override fun invoke(): CacheCall<R> = SimpleCacheCall(this, CacheArgs0)
    }
}

@ExperimentalKacheableApi
inline fun <reified R, G : Any> cache0(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
    noinline groupArgs: (G) -> CacheArgs,
): GroupedCache0<R, G> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : GroupedCache0<R, G>, CacheDefinition<R> by definition {
        override fun invoke(): CacheCall<R> = SimpleCacheCall(this, CacheArgs0)
        override fun group(key: G): CacheGroup = SimpleCacheGroup(name, groupArgs(key))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, reified R> cache1(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache1<P1, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache1<P1, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1): CacheCall<R> = SimpleCacheCall(this, CacheArgs1(p1))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, reified R> cache1(
    name: String,
    key: MainKeyPart<P1>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache1<P1, R, P1> = cache1(name, key, serializer<R>(), storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, R> cache1(
    name: String,
    key: MainKeyPart<P1>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache1<P1, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout)
    val cacheKey = key
    return object : GroupedCache1<P1, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1): CacheCall<R> = SimpleCacheCall(this, cacheKey.encode(p1))
        override fun group(key: P1): CacheGroup = SimpleCacheGroup(name, cacheKey.encode(key))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, reified R> cache2(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache2<P1, P2, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache2<P1, P2, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2): CacheCall<R> = SimpleCacheCall(this, CacheArgs2(p1, p2))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, reified R> cache2(
    name: String,
    key: MainSecondaryKey2<P1, P2>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache2<P1, P2, R, P1> = cache2(name, key, serializer<R>(), storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, R> cache2(
    name: String,
    key: MainSecondaryKey2<P1, P2>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache2<P1, P2, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout)
    val cacheKey = key
    return object : GroupedCache2<P1, P2, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2): CacheCall<R> =
            SimpleCacheCall(this, joinArgs(cacheKey.main.encode(p1), cacheKey.secondary.encode(p2)))

        override fun group(key: P1): CacheGroup =
            SimpleCacheGroup(name, joinArgs(cacheKey.main.encode(key), cacheKey.secondary.wildcardArgs))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, reified R> cache3(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache3<P1, P2, P3, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache3<P1, P2, P3, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3): CacheCall<R> = SimpleCacheCall(this, CacheArgs3(p1, p2, p3))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, reified R> cache3(
    name: String,
    key: MainSecondaryKey3<P1, P2, P3>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache3<P1, P2, P3, R, P1> = cache3(name, key, serializer<R>(), storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, R> cache3(
    name: String,
    key: MainSecondaryKey3<P1, P2, P3>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache3<P1, P2, P3, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout)
    val cacheKey = key
    return object : GroupedCache3<P1, P2, P3, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3): CacheCall<R> =
            SimpleCacheCall(this, joinArgs(cacheKey.main.encode(p1), cacheKey.secondary.encode(p2, p3)))

        override fun group(key: P1): CacheGroup =
            SimpleCacheGroup(name, joinArgs(cacheKey.main.encode(key), cacheKey.secondary.wildcardArgs))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, reified R> cache4(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache4<P1, P2, P3, P4, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache4<P1, P2, P3, P4, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): CacheCall<R> = SimpleCacheCall(this, CacheArgs4(p1, p2, p3, p4))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, reified R> cache4(
    name: String,
    key: MainSecondaryKey4<P1, P2, P3, P4>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache4<P1, P2, P3, P4, R, P1> = cache4(name, key, serializer<R>(), storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, R> cache4(
    name: String,
    key: MainSecondaryKey4<P1, P2, P3, P4>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache4<P1, P2, P3, P4, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout)
    val cacheKey = key
    return object : GroupedCache4<P1, P2, P3, P4, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): CacheCall<R> =
            SimpleCacheCall(this, joinArgs(cacheKey.main.encode(p1), cacheKey.secondary.encode(p2, p3, p4)))

        override fun group(key: P1): CacheGroup =
            SimpleCacheGroup(name, joinArgs(cacheKey.main.encode(key), cacheKey.secondary.wildcardArgs))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, reified R> cache5(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache5<P1, P2, P3, P4, P5, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache5<P1, P2, P3, P4, P5, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheCall<R> =
            SimpleCacheCall(this, CacheArgs5(p1, p2, p3, p4, p5))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, reified R> cache5(
    name: String,
    key: MainSecondaryKey5<P1, P2, P3, P4, P5>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache5<P1, P2, P3, P4, P5, R, P1> = cache5(name, key, serializer<R>(), storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, R> cache5(
    name: String,
    key: MainSecondaryKey5<P1, P2, P3, P4, P5>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache5<P1, P2, P3, P4, P5, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout)
    val cacheKey = key
    return object : GroupedCache5<P1, P2, P3, P4, P5, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheCall<R> =
            SimpleCacheCall(this, joinArgs(cacheKey.main.encode(p1), cacheKey.secondary.encode(p2, p3, p4, p5)))

        override fun group(key: P1): CacheGroup =
            SimpleCacheGroup(name, joinArgs(cacheKey.main.encode(key), cacheKey.secondary.wildcardArgs))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, reified R> cache6(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache6<P1, P2, P3, P4, P5, P6, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache6<P1, P2, P3, P4, P5, P6, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheCall<R> =
            SimpleCacheCall(this, CacheArgs6(p1, p2, p3, p4, p5, p6))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, reified R> cache6(
    name: String,
    key: MainSecondaryKey6<P1, P2, P3, P4, P5, P6>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache6<P1, P2, P3, P4, P5, P6, R, P1> = cache6(name, key, serializer<R>(), storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, R> cache6(
    name: String,
    key: MainSecondaryKey6<P1, P2, P3, P4, P5, P6>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache6<P1, P2, P3, P4, P5, P6, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout)
    val cacheKey = key
    return object : GroupedCache6<P1, P2, P3, P4, P5, P6, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheCall<R> =
            SimpleCacheCall(this, joinArgs(cacheKey.main.encode(p1), cacheKey.secondary.encode(p2, p3, p4, p5, p6)))

        override fun group(key: P1): CacheGroup =
            SimpleCacheGroup(name, joinArgs(cacheKey.main.encode(key), cacheKey.secondary.wildcardArgs))
    }
}

@ExperimentalKacheableApi
inline fun <K : Any, reified R> cache(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): KeyedCache<K, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : KeyedCache<K, R>, CacheDefinition<R> by definition {
        override fun invoke(key: K): CacheCall<R> = SimpleCacheCall(this, CacheArgs1(key))
    }
}

@ExperimentalKacheableApi
inline fun <K : Any, reified R> cache(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
    argsEncoder: CacheArgsEncoder<K>,
): KeyedCache<K, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : KeyedCache<K, R>, CacheDefinition<R> by definition {
        override fun invoke(key: K): CacheCall<R> = SimpleCacheCall(this, argsEncoder.encode(key))
    }
}

@ExperimentalKacheableApi
fun <P1 : Any, R> cache(
    name: String,
    key: MainKeyPart<P1>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache1<P1, R, P1> = cache1(name, key, serializer, storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, R> cache(
    name: String,
    key: MainSecondaryKey2<P1, P2>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache2<P1, P2, R, P1> = cache2(name, key, serializer, storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, R> cache(
    name: String,
    key: MainSecondaryKey3<P1, P2, P3>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache3<P1, P2, P3, R, P1> = cache3(name, key, serializer, storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, R> cache(
    name: String,
    key: MainSecondaryKey4<P1, P2, P3, P4>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache4<P1, P2, P3, P4, R, P1> = cache4(name, key, serializer, storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, R> cache(
    name: String,
    key: MainSecondaryKey5<P1, P2, P3, P4, P5>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache5<P1, P2, P3, P4, P5, R, P1> = cache5(name, key, serializer, storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, R> cache(
    name: String,
    key: MainSecondaryKey6<P1, P2, P3, P4, P5, P6>,
    serializer: KSerializer<R>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache6<P1, P2, P3, P4, P5, P6, R, P1> = cache6(name, key, serializer, storageLayout)

@ExperimentalKacheableApi
suspend operator fun <R> Kacheable.invoke(
    call: CacheCall<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R = invoke(
    call.definition.name,
    call.definition.serializer,
    *call.args.toParamsArray(),
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg calls: CacheCall<*>) {
    invalidate(*calls.map { it.definition.name to it.args.toParamsArray().toList() }.toTypedArray()) {}
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg groups: CacheGroup) {
    invalidate(*groups.map { it.name to it.args.toParamsArray().toList() }.toTypedArray()) {}
}

@ExperimentalKacheableApi
suspend fun <T> Kacheable.invalidate(vararg calls: CacheCall<*>, block: suspend () -> T): T {
    return invalidate(*calls.map { it.definition.name to it.args.toParamsArray().toList() }.toTypedArray(), block = block)
}

@ExperimentalKacheableApi
@Suppress("unused")
suspend fun <T> Kacheable.invalidate(vararg groups: CacheGroup, block: suspend () -> T): T {
    return invalidate(*groups.map { it.name to it.args.toParamsArray().toList() }.toTypedArray(), block = block)
}

@ExperimentalKacheableApi
operator fun <R> BlockingKacheable.invoke(
    call: CacheCall<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = invoke(
    call.definition.name,
    call.definition.serializer,
    *call.args.toParamsArray(),
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg calls: CacheCall<*>) {
    invalidate(*calls.map { it.definition.name to it.args.toParamsArray().toList() }.toTypedArray()) {}
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg groups: CacheGroup) {
    invalidate(*groups.map { it.name to it.args.toParamsArray().toList() }.toTypedArray()) {}
}

@ExperimentalKacheableApi
fun <T> BlockingKacheable.invalidate(vararg calls: CacheCall<*>, block: () -> T): T {
    return invalidate(*calls.map { it.definition.name to it.args.toParamsArray().toList() }.toTypedArray(), block = block)
}

@ExperimentalKacheableApi
@Suppress("unused")
fun <T> BlockingKacheable.invalidate(vararg groups: CacheGroup, block: () -> T): T {
    return invalidate(*groups.map { it.name to it.args.toParamsArray().toList() }.toTypedArray(), block = block)
}
