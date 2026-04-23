package com.github.dave08.kacheable

import com.github.dave08.kacheable.blocking.BlockingKacheable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

sealed interface CacheArgs {
    fun toParamsArray(): Array<out Any>
}

data object CacheArgs0 : CacheArgs {
    override fun toParamsArray(): Array<out Any> = emptyArray()
}

data class CacheArgs1<P1 : Any>(val p1: P1) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1)
}

data class CacheArgs2<P1 : Any, P2 : Any>(val p1: P1, val p2: P2) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1, p2)
}

data class CacheArgs3<P1 : Any, P2 : Any, P3 : Any>(val p1: P1, val p2: P2, val p3: P3) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1, p2, p3)
}

data class CacheArgs4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1, p2, p3, p4)
}

data class CacheArgs5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
    val p5: P5,
) : CacheArgs {
    override fun toParamsArray(): Array<out Any> = arrayOf<Any>(p1, p2, p3, p4, p5)
}

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

interface CacheDefinition<R> {
    val name: String
    val serializer: KSerializer<R>
}

interface CacheCall<R> {
    val definition: CacheDefinition<R>
    val args: CacheArgs
}

interface Cache0<R> : CacheDefinition<R> {
    operator fun invoke(): CacheCall<R>
}

interface Cache1<P1 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1): CacheCall<R>
}

interface Cache2<P1 : Any, P2 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2): CacheCall<R>
}

interface Cache3<P1 : Any, P2 : Any, P3 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3): CacheCall<R>
}

interface Cache4<P1 : Any, P2 : Any, P3 : Any, P4 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): CacheCall<R>
}

interface Cache5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheCall<R>
}

interface Cache6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheCall<R>
}

interface KeyedCache<K : Any, R> : CacheDefinition<R> {
    operator fun invoke(key: K): CacheCall<R>
}

@PublishedApi
internal data class SimpleCacheDefinition<R>(
    override val name: String,
    override val serializer: KSerializer<R>,
) : CacheDefinition<R>

@PublishedApi
internal data class SimpleCacheCall<R>(
    override val definition: CacheDefinition<R>,
    override val args: CacheArgs,
) : CacheCall<R>

inline fun <reified R> cache0(name: String): Cache0<R> {
    val definition = SimpleCacheDefinition(name, serializer<R>())
    return object : Cache0<R>, CacheDefinition<R> by definition {
        override fun invoke(): CacheCall<R> = SimpleCacheCall(this, CacheArgs0)
    }
}

inline fun <P1 : Any, reified R> cache1(name: String): Cache1<P1, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>())
    return object : Cache1<P1, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1): CacheCall<R> = SimpleCacheCall(this, CacheArgs1(p1))
    }
}

inline fun <P1 : Any, P2 : Any, reified R> cache2(name: String): Cache2<P1, P2, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>())
    return object : Cache2<P1, P2, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2): CacheCall<R> = SimpleCacheCall(this, CacheArgs2(p1, p2))
    }
}

inline fun <P1 : Any, P2 : Any, P3 : Any, reified R> cache3(name: String): Cache3<P1, P2, P3, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>())
    return object : Cache3<P1, P2, P3, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3): CacheCall<R> = SimpleCacheCall(this, CacheArgs3(p1, p2, p3))
    }
}

inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, reified R> cache4(name: String): Cache4<P1, P2, P3, P4, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>())
    return object : Cache4<P1, P2, P3, P4, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): CacheCall<R> = SimpleCacheCall(this, CacheArgs4(p1, p2, p3, p4))
    }
}

inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, reified R> cache5(name: String): Cache5<P1, P2, P3, P4, P5, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>())
    return object : Cache5<P1, P2, P3, P4, P5, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheCall<R> =
            SimpleCacheCall(this, CacheArgs5(p1, p2, p3, p4, p5))
    }
}

inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, reified R> cache6(name: String): Cache6<P1, P2, P3, P4, P5, P6, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>())
    return object : Cache6<P1, P2, P3, P4, P5, P6, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheCall<R> =
            SimpleCacheCall(this, CacheArgs6(p1, p2, p3, p4, p5, p6))
    }
}

inline fun <K : Any, reified R> cache(name: String): KeyedCache<K, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>())
    return object : KeyedCache<K, R>, CacheDefinition<R> by definition {
        override fun invoke(key: K): CacheCall<R> = SimpleCacheCall(this, CacheArgs1(key))
    }
}

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

suspend fun Kacheable.invalidate(vararg calls: CacheCall<*>) {
    invalidate(*calls.map { it.definition.name to it.args.toParamsArray().toList() }.toTypedArray()) {}
}

suspend fun <T> Kacheable.invalidate(vararg calls: CacheCall<*>, block: suspend () -> T): T {
    return invalidate(*calls.map { it.definition.name to it.args.toParamsArray().toList() }.toTypedArray(), block = block)
}

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

fun BlockingKacheable.invalidate(vararg calls: CacheCall<*>) {
    invalidate(*calls.map { it.definition.name to it.args.toParamsArray().toList() }.toTypedArray()) {}
}

fun <T> BlockingKacheable.invalidate(vararg calls: CacheCall<*>, block: () -> T): T {
    return invalidate(*calls.map { it.definition.name to it.args.toParamsArray().toList() }.toTypedArray(), block = block)
}
