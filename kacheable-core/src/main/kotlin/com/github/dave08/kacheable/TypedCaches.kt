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
inline fun <P1 : Any, reified R, G : Any> cache1(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
    noinline groupArgs: (G) -> CacheArgs,
): GroupedCache1<P1, R, G> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : GroupedCache1<P1, R, G>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1): CacheCall<R> = SimpleCacheCall(this, CacheArgs1(p1))
        override fun group(key: G): CacheGroup = SimpleCacheGroup(name, groupArgs(key))
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
inline fun <P1 : Any, P2 : Any, reified R, G : Any> cache2(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
    noinline groupArgs: (G) -> CacheArgs,
): GroupedCache2<P1, P2, R, G> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : GroupedCache2<P1, P2, R, G>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2): CacheCall<R> = SimpleCacheCall(this, CacheArgs2(p1, p2))
        override fun group(key: G): CacheGroup = SimpleCacheGroup(name, groupArgs(key))
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
inline fun <P1 : Any, P2 : Any, P3 : Any, reified R, G : Any> cache3(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
    noinline groupArgs: (G) -> CacheArgs,
): GroupedCache3<P1, P2, P3, R, G> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : GroupedCache3<P1, P2, P3, R, G>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3): CacheCall<R> = SimpleCacheCall(this, CacheArgs3(p1, p2, p3))
        override fun group(key: G): CacheGroup = SimpleCacheGroup(name, groupArgs(key))
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
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, reified R, G : Any> cache4(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
    noinline groupArgs: (G) -> CacheArgs,
): GroupedCache4<P1, P2, P3, P4, R, G> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : GroupedCache4<P1, P2, P3, P4, R, G>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): CacheCall<R> = SimpleCacheCall(this, CacheArgs4(p1, p2, p3, p4))
        override fun group(key: G): CacheGroup = SimpleCacheGroup(name, groupArgs(key))
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
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, reified R, G : Any> cache5(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
    noinline groupArgs: (G) -> CacheArgs,
): GroupedCache5<P1, P2, P3, P4, P5, R, G> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : GroupedCache5<P1, P2, P3, P4, P5, R, G>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheCall<R> =
            SimpleCacheCall(this, CacheArgs5(p1, p2, p3, p4, p5))
        override fun group(key: G): CacheGroup = SimpleCacheGroup(name, groupArgs(key))
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
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, reified R, G : Any> cache6(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
    noinline groupArgs: (G) -> CacheArgs,
): GroupedCache6<P1, P2, P3, P4, P5, P6, R, G> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : GroupedCache6<P1, P2, P3, P4, P5, P6, R, G>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheCall<R> =
            SimpleCacheCall(this, CacheArgs6(p1, p2, p3, p4, p5, p6))
        override fun group(key: G): CacheGroup = SimpleCacheGroup(name, groupArgs(key))
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
