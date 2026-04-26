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
sealed interface CacheStorage {
    data object String : CacheStorage, SupportsValueReturn
    data object HashMap : CacheStorage, SupportsValueReturn, SupportsMapReturn
    data object Set : CacheStorage
    data object List : CacheStorage
    data object Int : CacheStorage
}

@ExperimentalKacheableApi
sealed interface SupportsValueReturn

@ExperimentalKacheableApi
sealed interface SupportsMapReturn

@ExperimentalKacheableApi
interface CacheReturn<R> {
    val serializer: KSerializer<R>
    val codec: CacheValueCodec<R>
}

@ExperimentalKacheableApi
interface HashMapCacheReturn<R> : CacheReturn<R>

@ExperimentalKacheableApi
data class ValueCacheReturn<R>(
    override val serializer: KSerializer<R>,
    override val codec: CacheValueCodec<R> = cacheValueCodec(serializer),
) : HashMapCacheReturn<R>

@ExperimentalKacheableApi
data class MapCacheReturn<K : Any, R>(
    override val serializer: KSerializer<Map<K, R>>,
    override val codec: CacheValueCodec<Map<K, R>> = cacheValueCodec(serializer),
) : HashMapCacheReturn<Map<K, R>>

@ExperimentalKacheableApi
inline fun <reified R> value(
    codec: CacheValueCodec<R> = cacheValueCodec(serializer<R>()),
): ValueCacheReturn<R> = ValueCacheReturn(serializer<R>(), codec)

@ExperimentalKacheableApi
fun <R> value(
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
): ValueCacheReturn<R> = ValueCacheReturn(serializer, codec)

@ExperimentalKacheableApi
inline fun <reified K : Any, reified R> map(
    codec: CacheValueCodec<Map<K, R>> = cacheValueCodec(serializer<Map<K, R>>()),
): MapCacheReturn<K, R> = MapCacheReturn(serializer<Map<K, R>>(), codec)

@ExperimentalKacheableApi
fun <K : Any, R> map(
    serializer: KSerializer<Map<K, R>>,
    codec: CacheValueCodec<Map<K, R>> = cacheValueCodec(serializer),
): MapCacheReturn<K, R> = MapCacheReturn(serializer, codec)

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
    val codec: CacheValueCodec<R>
    val storageLayout: CacheStorageLayout
}

@ExperimentalKacheableApi
interface CacheEntryRef<R> {
    val definition: CacheDefinition<R>
    val args: CacheArgs
    val keyGroups: CacheKeyGroups
        get() = CacheKeyGroups(args)
}

@ExperimentalKacheableApi
interface CacheEntryPartRef {
    val name: String
    val args: CacheArgs
    val storageLayout: CacheStorageLayout?
    val keyGroups: CacheKeyGroups
        get() = CacheKeyGroups(args)
}

@ExperimentalKacheableApi
interface StoredCacheEntryRef<S : CacheStorage> {
    val name: String
    val keyGroups: CacheKeyGroups
    val storageLayout: CacheStorageLayout
}

@ExperimentalKacheableApi
data class HashMapCacheEntryRef(
    override val name: String,
    override val keyGroups: CacheKeyGroups,
) : StoredCacheEntryRef<CacheStorage.HashMap> {
    override val storageLayout: CacheStorageLayout = CacheStorageLayout.HashValue
}

@ExperimentalKacheableApi
data class CacheKeyGroups(
    val main: CacheArgs,
    val secondary: CacheArgs? = null,
) {
    val flattened: CacheArgs = secondary?.let { joinArgs(main, it) } ?: main
}

@ExperimentalKacheableApi
data class CacheKeyPartRef<G : Any>(
    val key: MainKeyPart<G>,
    val value: G,
) {
    val args: CacheArgs = key.encode(value)
}

@ExperimentalKacheableApi
interface Cache0<R> : CacheDefinition<R> {
    operator fun invoke(): CacheEntryRef<R>
    fun key(): CacheEntryRef<R> = invoke()
}

@ExperimentalKacheableApi
interface Cache1<P1 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1): CacheEntryRef<R>
    fun key(p1: P1): CacheEntryRef<R> = invoke(p1)
}

@ExperimentalKacheableApi
interface Cache2<P1 : Any, P2 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2): CacheEntryRef<R>
    fun key(p1: P1, p2: P2): CacheEntryRef<R> = invoke(p1, p2)
}

@ExperimentalKacheableApi
interface Cache3<P1 : Any, P2 : Any, P3 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3): CacheEntryRef<R>
    fun key(p1: P1, p2: P2, p3: P3): CacheEntryRef<R> = invoke(p1, p2, p3)
}

@ExperimentalKacheableApi
interface Cache4<P1 : Any, P2 : Any, P3 : Any, P4 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): CacheEntryRef<R>
    fun key(p1: P1, p2: P2, p3: P3, p4: P4): CacheEntryRef<R> = invoke(p1, p2, p3, p4)
}

@ExperimentalKacheableApi
interface Cache5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheEntryRef<R>
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheEntryRef<R> = invoke(p1, p2, p3, p4, p5)
}

@ExperimentalKacheableApi
interface Cache6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, R> : CacheDefinition<R> {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheEntryRef<R>
    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheEntryRef<R> = invoke(p1, p2, p3, p4, p5, p6)
}

@ExperimentalKacheableApi
interface KeyedCache<K : Any, R> : CacheDefinition<R> {
    operator fun invoke(key: K): CacheEntryRef<R>
    fun key(key: K): CacheEntryRef<R> = invoke(key)
}

@ExperimentalKacheableApi
interface GroupedCache0<R, G : Any> : Cache0<R> {
    fun keyPart(part: CacheKeyPartRef<G>): CacheEntryPartRef
}

@ExperimentalKacheableApi
interface GroupedCache1<P1 : Any, R, G : Any> : Cache1<P1, R> {
    fun keyPart(part: CacheKeyPartRef<G>): CacheEntryPartRef
}

@ExperimentalKacheableApi
interface GroupedCache2<P1 : Any, P2 : Any, R, G : Any> : Cache2<P1, P2, R> {
    fun keyPart(part: CacheKeyPartRef<G>): CacheEntryPartRef
}

@ExperimentalKacheableApi
interface GroupedCache3<P1 : Any, P2 : Any, P3 : Any, R, G : Any> : Cache3<P1, P2, P3, R> {
    fun keyPart(part: CacheKeyPartRef<G>): CacheEntryPartRef
}

@ExperimentalKacheableApi
interface GroupedCache4<P1 : Any, P2 : Any, P3 : Any, P4 : Any, R, G : Any> : Cache4<P1, P2, P3, P4, R> {
    fun keyPart(part: CacheKeyPartRef<G>): CacheEntryPartRef
}

@ExperimentalKacheableApi
interface GroupedCache5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, R, G : Any> : Cache5<P1, P2, P3, P4, P5, R> {
    fun keyPart(part: CacheKeyPartRef<G>): CacheEntryPartRef
}

@ExperimentalKacheableApi
interface GroupedCache6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, R, G : Any> : Cache6<P1, P2, P3, P4, P5, P6, R> {
    fun keyPart(part: CacheKeyPartRef<G>): CacheEntryPartRef
}

@ExperimentalKacheableApi
data class HashMapMainKey<P1 : Any>(
    val name: String,
    val key: MainKeyPart<P1>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.encode(p1)))

    fun keyPart(value: P1): CacheKeyPartRef<P1> = key.invoke(value)

    fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef =
        SimpleCacheEntryPartRef(name, key.encode(part.value), CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache1<P1 : Any>(
    val name: String,
    val key: MainKeyPart<P1>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.encode(p1)))

    fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef =
        SimpleCacheEntryPartRef(name, key.encode(part.value), CacheStorageLayout.HashValue)
}

@ExperimentalKacheableApi
data class HashMapStoredCache2<P1 : Any, P2 : Any>(
    val name: String,
    val key: MainSecondaryKey2<P1, P2>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.main.encode(p1)))

    fun key(p1: P1, p2: P2): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2),
            ),
        )

    fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef {
        val mainArgs = key.main.encode(part.value)
        return groupedEntryPartRef(name, mainArgs, key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
    }
}

@ExperimentalKacheableApi
data class HashMapStoredCache3<P1 : Any, P2 : Any, P3 : Any>(
    val name: String,
    val key: MainSecondaryKey3<P1, P2, P3>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.main.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2, p3),
            ),
        )

    fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef {
        val mainArgs = key.main.encode(part.value)
        return groupedEntryPartRef(name, mainArgs, key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
    }
}

@ExperimentalKacheableApi
data class HashMapStoredCache4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val name: String,
    val key: MainSecondaryKey4<P1, P2, P3, P4>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.main.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3, p4: P4): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2, p3, p4),
            ),
        )

    fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef {
        val mainArgs = key.main.encode(part.value)
        return groupedEntryPartRef(name, mainArgs, key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
    }
}

@ExperimentalKacheableApi
data class HashMapStoredCache5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val name: String,
    val key: MainSecondaryKey5<P1, P2, P3, P4, P5>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.main.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2, p3, p4, p5),
            ),
        )

    fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef {
        val mainArgs = key.main.encode(part.value)
        return groupedEntryPartRef(name, mainArgs, key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
    }
}

@ExperimentalKacheableApi
data class HashMapStoredCache6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val name: String,
    val key: MainSecondaryKey6<P1, P2, P3, P4, P5, P6>,
) {
    fun key(p1: P1): HashMapCacheEntryRef =
        HashMapCacheEntryRef(name, CacheKeyGroups(main = key.main.encode(p1)))

    fun key(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): HashMapCacheEntryRef =
        HashMapCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2, p3, p4, p5, p6),
            ),
        )

    fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef {
        val mainArgs = key.main.encode(part.value)
        return groupedEntryPartRef(name, mainArgs, key.secondary.wildcardArgs, CacheStorageLayout.HashValue)
    }
}

@ExperimentalKacheableApi
interface MainKeyPart<P1 : Any> : CacheArgsEncoder<P1> {
    val label: String
    operator fun invoke(value: P1): CacheKeyPartRef<P1> = CacheKeyPartRef(this, value)
}

@ExperimentalKacheableApi
interface KeyPart<P1 : Any> : CacheArgsEncoder<P1> {
    val wildcardArgs: CacheArgs
}

@ExperimentalKacheableApi
typealias MainKey<P1> = MainKeyPart<P1>

@ExperimentalKacheableApi
typealias SecondaryKeyPart<P1> = KeyPart<P1>

@ExperimentalKacheableApi
typealias KeyMapper<P1> = KeyPart<P1>

@ExperimentalKacheableApi
data class SecondaryKeyComposition2<P1 : Any, P2 : Any>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
) {
    fun encode(p1: P1, p2: P2): CacheArgs = joinArgs(first.encode(p1), second.encode(p2))
    val wildcardArgs: CacheArgs = joinArgs(first.wildcardArgs, second.wildcardArgs)
}

@ExperimentalKacheableApi
data class SecondaryKeyComposition3<P1 : Any, P2 : Any, P3 : Any>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
) {
    fun encode(p1: P1, p2: P2, p3: P3): CacheArgs = joinArgs(first.encode(p1), second.encode(p2), third.encode(p3))
    val wildcardArgs: CacheArgs = joinArgs(first.wildcardArgs, second.wildcardArgs, third.wildcardArgs)
}

@ExperimentalKacheableApi
data class SecondaryKeyComposition4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
    val fourth: KeyPart<P4>,
) {
    fun encode(p1: P1, p2: P2, p3: P3, p4: P4): CacheArgs =
        joinArgs(first.encode(p1), second.encode(p2), third.encode(p3), fourth.encode(p4))

    val wildcardArgs: CacheArgs =
        joinArgs(first.wildcardArgs, second.wildcardArgs, third.wildcardArgs, fourth.wildcardArgs)
}

@ExperimentalKacheableApi
data class SecondaryKeyComposition5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
    val fourth: KeyPart<P4>,
    val fifth: KeyPart<P5>,
) {
    fun encode(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheArgs =
        joinArgs(first.encode(p1), second.encode(p2), third.encode(p3), fourth.encode(p4), fifth.encode(p5))

    val wildcardArgs: CacheArgs =
        joinArgs(first.wildcardArgs, second.wildcardArgs, third.wildcardArgs, fourth.wildcardArgs, fifth.wildcardArgs)
}

@ExperimentalKacheableApi
data class MainSecondaryKey2<P1 : Any, P2 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: KeyPart<P2>,
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
    override val codec: CacheValueCodec<R> = cacheValueCodec(serializer),
) : CacheDefinition<R>

@PublishedApi
internal data class SimpleCacheEntryRef<R>(
    override val definition: CacheDefinition<R>,
    override val args: CacheArgs,
    override val keyGroups: CacheKeyGroups = CacheKeyGroups(args),
) : CacheEntryRef<R>

@PublishedApi
internal data class SimpleCacheEntryPartRef(
    override val name: String,
    override val args: CacheArgs,
    override val storageLayout: CacheStorageLayout? = null,
    override val keyGroups: CacheKeyGroups = CacheKeyGroups(args),
) : CacheEntryPartRef

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
) : KeyPart<P1> {
    override fun encode(value: P1): CacheArgs = encoder.encode(value)
}

@PublishedApi
internal fun joinArgs(vararg segments: CacheArgs): CacheArgs {
    val params = segments.flatMap { it.toParamsArray().toList() }
    return CachePatternArgs(*params.toTypedArray())
}

@PublishedApi
internal fun wildcardArgs(size: Int): CacheArgs = CachePatternArgs(*Array(size) { CacheWildcard as Any })

@PublishedApi
internal fun <R> groupedEntryRef(
    definition: CacheDefinition<R>,
    mainArgs: CacheArgs,
    secondaryArgs: CacheArgs,
): CacheEntryRef<R> = SimpleCacheEntryRef(
    definition = definition,
    args = joinArgs(mainArgs, secondaryArgs),
    keyGroups = CacheKeyGroups(main = mainArgs, secondary = secondaryArgs),
)

@PublishedApi
internal fun groupedEntryPartRef(
    name: String,
    mainArgs: CacheArgs,
    secondaryArgs: CacheArgs,
    storageLayout: CacheStorageLayout? = null,
): CacheEntryPartRef = SimpleCacheEntryPartRef(
    name = name,
    args = joinArgs(mainArgs, secondaryArgs),
    storageLayout = storageLayout,
    keyGroups = CacheKeyGroups(main = mainArgs, secondary = secondaryArgs),
)

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
): KeyPart<P1> {
    require(values.isNotEmpty()) { "secondaryKeyPart requires at least one value extractor" }
    return SimpleSecondaryKeyPart(
        encoder = cacheArgsEncoder { value -> argsOf(*values.map { extractor -> extractor(value) }.toTypedArray()) },
        wildcardArgs = wildcardArgs(values.size),
    )
}

@ExperimentalKacheableApi
fun <P1 : Any> keyMapper(): KeyPart<P1> = secondaryKeyPart({ it })

@ExperimentalKacheableApi
fun <P1 : Any> keyMapper(
    vararg values: (P1) -> Any,
): KeyPart<P1> = secondaryKeyPart(*values)

@ExperimentalKacheableApi
fun <P1 : Any> key(): KeyPart<P1> = keyMapper()

@ExperimentalKacheableApi
fun <P1 : Any> key(
    vararg values: (P1) -> Any,
): KeyPart<P1> = keyMapper(*values)

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    mapper: KeyPart<P1> = keyMapper(),
): MainKey<P1> = SimpleMainKeyPart(label, mapper)

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    storedAs: CacheStorage.HashMap,
    mapper: KeyPart<P1> = keyMapper(),
): HashMapMainKey<P1> = HashMapMainKey(label, mainKey(label, mapper))

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    vararg values: (P1) -> Any,
): MainKey<P1> = mainKey(label, keyMapper(*values))

@ExperimentalKacheableApi
fun <P1 : Any> mainKey(
    label: String,
    storedAs: CacheStorage.HashMap,
    vararg values: (P1) -> Any,
): HashMapMainKey<P1> = HashMapMainKey(label, mainKey(label, *values))

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> MainKeyPart<P1>.plus(
    secondary: KeyPart<P2>,
): MainSecondaryKey2<P1, P2> = MainSecondaryKey2(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> HashMapMainKey<P1>.plus(
    secondary: KeyPart<P2>,
): HashMapStoredCache2<P1, P2> = HashMapStoredCache2(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition2<P2, P3>,
): HashMapStoredCache3<P1, P2, P3> = HashMapStoredCache3(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition3<P2, P3, P4>,
): HashMapStoredCache4<P1, P2, P3, P4> = HashMapStoredCache4(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition4<P2, P3, P4, P5>,
): HashMapStoredCache5<P1, P2, P3, P4, P5> = HashMapStoredCache5(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition5<P2, P3, P4, P5, P6>,
): HashMapStoredCache6<P1, P2, P3, P4, P5, P6> = HashMapStoredCache6(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> KeyPart<P1>.plus(
    other: KeyPart<P2>,
): SecondaryKeyComposition2<P1, P2> = SecondaryKeyComposition2(this, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> SecondaryKeyComposition2<P1, P2>.plus(
    other: KeyPart<P3>,
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
    other: KeyPart<P4>,
): SecondaryKeyComposition4<P1, P2, P3, P4> = SecondaryKeyComposition4(first, second, third, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition3<P2, P3, P4>,
): MainSecondaryKey4<P1, P2, P3, P4> = MainSecondaryKey4(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> SecondaryKeyComposition4<P1, P2, P3, P4>.plus(
    other: KeyPart<P5>,
): SecondaryKeyComposition5<P1, P2, P3, P4, P5> = SecondaryKeyComposition5(first, second, third, fourth, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition4<P2, P3, P4, P5>,
): MainSecondaryKey5<P1, P2, P3, P4, P5> = MainSecondaryKey5(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> SecondaryKeyComposition5<P1, P2, P3, P4, P5>.plus(
    other: KeyPart<P6>,
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
        override fun invoke(): CacheEntryRef<R> = SimpleCacheEntryRef(this, CacheArgs0)
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
        override fun invoke(): CacheEntryRef<R> = SimpleCacheEntryRef(this, CacheArgs0)
        override fun keyPart(part: CacheKeyPartRef<G>): CacheEntryPartRef =
            SimpleCacheEntryPartRef(name, groupArgs(part.value), storageLayout)
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, reified R> cache1(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache1<P1, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache1<P1, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1): CacheEntryRef<R> = SimpleCacheEntryRef(this, CacheArgs1(p1))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, reified R> cache1(
    name: String,
    key: MainKeyPart<P1>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache1<P1, R, P1> = cache1(name, key, serializer<R>(), storageLayout = storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, R> cache1(
    name: String,
    key: MainKeyPart<P1>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache1<P1, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout, codec)
    val cacheKey = key
    return object : GroupedCache1<P1, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1): CacheEntryRef<R> = SimpleCacheEntryRef(this, cacheKey.encode(p1))
        override fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef =
            SimpleCacheEntryPartRef(name, cacheKey.encode(part.value), storageLayout)
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, reified R> cache2(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache2<P1, P2, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache2<P1, P2, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2): CacheEntryRef<R> = SimpleCacheEntryRef(this, CacheArgs2(p1, p2))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, reified R> cache2(
    name: String,
    key: MainSecondaryKey2<P1, P2>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache2<P1, P2, R, P1> = cache2(name, key, serializer<R>(), storageLayout = storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, R> cache2(
    name: String,
    key: MainSecondaryKey2<P1, P2>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache2<P1, P2, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout, codec)
    val cacheKey = key
    return object : GroupedCache2<P1, P2, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2): CacheEntryRef<R> {
            val mainArgs = cacheKey.main.encode(p1)
            val secondaryArgs = cacheKey.secondary.encode(p2)
            return groupedEntryRef(this, mainArgs, secondaryArgs)
        }

        override fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef {
            val mainArgs = cacheKey.main.encode(part.value)
            val secondaryArgs = cacheKey.secondary.wildcardArgs
            return groupedEntryPartRef(name, mainArgs, secondaryArgs, storageLayout)
        }
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, reified R> cache3(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache3<P1, P2, P3, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache3<P1, P2, P3, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3): CacheEntryRef<R> = SimpleCacheEntryRef(this, CacheArgs3(p1, p2, p3))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, reified R> cache3(
    name: String,
    key: MainSecondaryKey3<P1, P2, P3>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache3<P1, P2, P3, R, P1> = cache3(name, key, serializer<R>(), storageLayout = storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, R> cache3(
    name: String,
    key: MainSecondaryKey3<P1, P2, P3>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache3<P1, P2, P3, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout, codec)
    val cacheKey = key
    return object : GroupedCache3<P1, P2, P3, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3): CacheEntryRef<R> {
            val mainArgs = cacheKey.main.encode(p1)
            val secondaryArgs = cacheKey.secondary.encode(p2, p3)
            return groupedEntryRef(this, mainArgs, secondaryArgs)
        }

        override fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef {
            val mainArgs = cacheKey.main.encode(part.value)
            val secondaryArgs = cacheKey.secondary.wildcardArgs
            return groupedEntryPartRef(name, mainArgs, secondaryArgs, storageLayout)
        }
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, reified R> cache4(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache4<P1, P2, P3, P4, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache4<P1, P2, P3, P4, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): CacheEntryRef<R> = SimpleCacheEntryRef(this, CacheArgs4(p1, p2, p3, p4))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, reified R> cache4(
    name: String,
    key: MainSecondaryKey4<P1, P2, P3, P4>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache4<P1, P2, P3, P4, R, P1> = cache4(name, key, serializer<R>(), storageLayout = storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, R> cache4(
    name: String,
    key: MainSecondaryKey4<P1, P2, P3, P4>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache4<P1, P2, P3, P4, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout, codec)
    val cacheKey = key
    return object : GroupedCache4<P1, P2, P3, P4, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): CacheEntryRef<R> {
            val mainArgs = cacheKey.main.encode(p1)
            val secondaryArgs = cacheKey.secondary.encode(p2, p3, p4)
            return groupedEntryRef(this, mainArgs, secondaryArgs)
        }

        override fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef {
            val mainArgs = cacheKey.main.encode(part.value)
            val secondaryArgs = cacheKey.secondary.wildcardArgs
            return groupedEntryPartRef(name, mainArgs, secondaryArgs, storageLayout)
        }
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, reified R> cache5(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache5<P1, P2, P3, P4, P5, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache5<P1, P2, P3, P4, P5, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheEntryRef<R> =
            SimpleCacheEntryRef(this, CacheArgs5(p1, p2, p3, p4, p5))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, reified R> cache5(
    name: String,
    key: MainSecondaryKey5<P1, P2, P3, P4, P5>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache5<P1, P2, P3, P4, P5, R, P1> = cache5(name, key, serializer<R>(), storageLayout = storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, R> cache5(
    name: String,
    key: MainSecondaryKey5<P1, P2, P3, P4, P5>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache5<P1, P2, P3, P4, P5, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout, codec)
    val cacheKey = key
    return object : GroupedCache5<P1, P2, P3, P4, P5, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheEntryRef<R> {
            val mainArgs = cacheKey.main.encode(p1)
            val secondaryArgs = cacheKey.secondary.encode(p2, p3, p4, p5)
            return groupedEntryRef(this, mainArgs, secondaryArgs)
        }

        override fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef {
            val mainArgs = cacheKey.main.encode(part.value)
            val secondaryArgs = cacheKey.secondary.wildcardArgs
            return groupedEntryPartRef(name, mainArgs, secondaryArgs, storageLayout)
        }
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, reified R> cache6(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): Cache6<P1, P2, P3, P4, P5, P6, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : Cache6<P1, P2, P3, P4, P5, P6, R>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheEntryRef<R> =
            SimpleCacheEntryRef(this, CacheArgs6(p1, p2, p3, p4, p5, p6))
    }
}

@ExperimentalKacheableApi
inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, reified R> cache6(
    name: String,
    key: MainSecondaryKey6<P1, P2, P3, P4, P5, P6>,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache6<P1, P2, P3, P4, P5, P6, R, P1> = cache6(name, key, serializer<R>(), storageLayout = storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, R> cache6(
    name: String,
    key: MainSecondaryKey6<P1, P2, P3, P4, P5, P6>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache6<P1, P2, P3, P4, P5, P6, R, P1> {
    val definition = SimpleCacheDefinition(name, serializer, storageLayout, codec)
    val cacheKey = key
    return object : GroupedCache6<P1, P2, P3, P4, P5, P6, R, P1>, CacheDefinition<R> by definition {
        override fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheEntryRef<R> {
            val mainArgs = cacheKey.main.encode(p1)
            val secondaryArgs = cacheKey.secondary.encode(p2, p3, p4, p5, p6)
            return groupedEntryRef(this, mainArgs, secondaryArgs)
        }

        override fun keyPart(part: CacheKeyPartRef<P1>): CacheEntryPartRef {
            val mainArgs = cacheKey.main.encode(part.value)
            val secondaryArgs = cacheKey.secondary.wildcardArgs
            return groupedEntryPartRef(name, mainArgs, secondaryArgs, storageLayout)
        }
    }
}

@ExperimentalKacheableApi
inline fun <K : Any, reified R> cache(
    name: String,
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): KeyedCache<K, R> {
    val definition = SimpleCacheDefinition(name, serializer<R>(), storageLayout)
    return object : KeyedCache<K, R>, CacheDefinition<R> by definition {
        override fun invoke(key: K): CacheEntryRef<R> = SimpleCacheEntryRef(this, CacheArgs1(key))
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
        override fun invoke(key: K): CacheEntryRef<R> = SimpleCacheEntryRef(this, argsEncoder.encode(key))
    }
}

@ExperimentalKacheableApi
fun <P1 : Any, R> cache(
    name: String,
    key: MainKeyPart<P1>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache1<P1, R, P1> = cache1(name, key, serializer, codec, storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, R> cache(
    name: String,
    key: MainSecondaryKey2<P1, P2>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache2<P1, P2, R, P1> = cache2(name, key, serializer, codec, storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, R> cache(
    name: String,
    key: MainSecondaryKey3<P1, P2, P3>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache3<P1, P2, P3, R, P1> = cache3(name, key, serializer, codec, storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, R> cache(
    name: String,
    key: MainSecondaryKey4<P1, P2, P3, P4>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache4<P1, P2, P3, P4, R, P1> = cache4(name, key, serializer, codec, storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, R> cache(
    name: String,
    key: MainSecondaryKey5<P1, P2, P3, P4, P5>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache5<P1, P2, P3, P4, P5, R, P1> = cache5(name, key, serializer, codec, storageLayout)

@ExperimentalKacheableApi
fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, R> cache(
    name: String,
    key: MainSecondaryKey6<P1, P2, P3, P4, P5, P6>,
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue,
): GroupedCache6<P1, P2, P3, P4, P5, P6, R, P1> = cache6(name, key, serializer, codec, storageLayout)

@ExperimentalKacheableApi
suspend operator fun <R> Kacheable.invoke(
    entryRef: CacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R = invoke(
    name = entryRef.definition.name,
    codec = entryRef.definition.codec,
    keyGroups = entryRef.keyGroups,
    storageLayout = entryRef.definition.storageLayout,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
suspend operator fun <R> Kacheable.invoke(
    entryRef: HashMapCacheEntryRef,
    returnsAs: HashMapCacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    keyGroups = entryRef.keyGroups,
    storageLayout = entryRef.storageLayout,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg entryRefs: CacheEntryRef<*>) {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.definition.name, entryRef.keyGroups, entryRef.definition.storageLayout) {}
    }
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg partRefs: CacheEntryPartRef) {
    partRefs.forEach { partRef ->
        val storageLayout = partRef.storageLayout
        if (storageLayout == null)
            invalidate(partRef.name to partRef.args.toParamsArray().toList()) {}
        else
            invalidate(partRef.name, partRef.keyGroups, storageLayout) {}
    }
}

@ExperimentalKacheableApi
suspend fun <T> Kacheable.invalidate(vararg entryRefs: CacheEntryRef<*>, block: suspend () -> T): T {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.definition.name, entryRef.keyGroups, entryRef.definition.storageLayout) {}
    }
    return block()
}

@ExperimentalKacheableApi
@Suppress("unused")
suspend fun <T> Kacheable.invalidate(vararg partRefs: CacheEntryPartRef, block: suspend () -> T): T {
    partRefs.forEach { partRef ->
        val storageLayout = partRef.storageLayout
        if (storageLayout == null)
            invalidate(partRef.name to partRef.args.toParamsArray().toList()) {}
        else
            invalidate(partRef.name, partRef.keyGroups, storageLayout) {}
    }
    return block()
}

@ExperimentalKacheableApi
operator fun <R> BlockingKacheable.invoke(
    entryRef: CacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = invoke(
    name = entryRef.definition.name,
    codec = entryRef.definition.codec,
    keyGroups = entryRef.keyGroups,
    storageLayout = entryRef.definition.storageLayout,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
operator fun <R> BlockingKacheable.invoke(
    entryRef: HashMapCacheEntryRef,
    returnsAs: HashMapCacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    keyGroups = entryRef.keyGroups,
    storageLayout = entryRef.storageLayout,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg entryRefs: CacheEntryRef<*>) {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.definition.name, entryRef.keyGroups, entryRef.definition.storageLayout) {}
    }
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg partRefs: CacheEntryPartRef) {
    partRefs.forEach { partRef ->
        val storageLayout = partRef.storageLayout
        if (storageLayout == null)
            invalidate(partRef.name to partRef.args.toParamsArray().toList()) {}
        else
            invalidate(partRef.name, partRef.keyGroups, storageLayout) {}
    }
}

@ExperimentalKacheableApi
fun <T> BlockingKacheable.invalidate(vararg entryRefs: CacheEntryRef<*>, block: () -> T): T {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.definition.name, entryRef.keyGroups, entryRef.definition.storageLayout) {}
    }
    return block()
}

@ExperimentalKacheableApi
@Suppress("unused")
fun <T> BlockingKacheable.invalidate(vararg partRefs: CacheEntryPartRef, block: () -> T): T {
    partRefs.forEach { partRef ->
        val storageLayout = partRef.storageLayout
        if (storageLayout == null)
            invalidate(partRef.name to partRef.args.toParamsArray().toList()) {}
        else
            invalidate(partRef.name, partRef.keyGroups, storageLayout) {}
    }
    return block()
}
