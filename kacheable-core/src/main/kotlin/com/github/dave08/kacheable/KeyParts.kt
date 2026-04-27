@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
data class CacheKeyGroups(
    val main: CacheArgs,
    val secondary: CacheArgs? = null,
) {
    val flattened: CacheArgs = secondary?.let { joinArgs(main, it) } ?: main
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
interface MainKeyPart<P1 : Any> {
    val label: String
    fun encode(value: P1): CacheArgs
}

@ExperimentalKacheableApi
interface KeyPart<P1 : Any> {
    fun encode(value: P1): CacheArgs
    val wildcardArgs: CacheArgs
}

@ExperimentalKacheableApi
typealias MainKey<P1> = MainKeyPart<P1>

@ExperimentalKacheableApi
typealias SecondaryKeyPart<P1> = KeyPart<P1>

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
internal data class SimpleCacheEntryPartRef(
    override val name: String,
    override val args: CacheArgs,
    override val storageLayout: CacheStorageLayout? = null,
    override val keyGroups: CacheKeyGroups = CacheKeyGroups(args),
) : CacheEntryPartRef

@PublishedApi
internal data class SimpleMainKeyPart<P1 : Any>(
    override val label: String,
    private val encoder: (P1) -> CacheArgs,
) : MainKeyPart<P1> {
    override fun encode(value: P1): CacheArgs = encoder(value)
}

@PublishedApi
internal data class SimpleSecondaryKeyPart<P1 : Any>(
    private val encoders: List<(P1) -> Any>,
    override val wildcardArgs: CacheArgs,
) : KeyPart<P1> {
    override fun encode(value: P1): CacheArgs = argsOf(*encoders.map { it(value) }.toTypedArray())
}

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
fun <P1 : Any> secondaryKeyPart(
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

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> MainKeyPart<P1>.plus(
    secondary: KeyPart<P2>,
): MainSecondaryKey2<P1, P2> = MainSecondaryKey2(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> HashMapMainKey<P1>.plus(
    secondary: KeyPart<P2>,
): HashMapStoredCache2<P1, P2> = HashMapStoredCache2(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> SetMainKey<P1>.plus(
    member: KeyPart<P2>,
): SetStoredCache2<P1, P2> = SetStoredCache2(name, key + member)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> KeyPart<P1>.plus(
    other: KeyPart<P2>,
): SecondaryKeyComposition2<P1, P2> = SecondaryKeyComposition2(this, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> SecondaryKeyComposition2<P1, P2>.plus(
    other: KeyPart<P3>,
): SecondaryKeyComposition3<P1, P2, P3> = SecondaryKeyComposition3(first, second, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition2<P2, P3>,
): MainSecondaryKey3<P1, P2, P3> = MainSecondaryKey3(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition2<P2, P3>,
): HashMapStoredCache3<P1, P2, P3> = HashMapStoredCache3(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> SecondaryKeyComposition3<P1, P2, P3>.plus(
    other: KeyPart<P4>,
): SecondaryKeyComposition4<P1, P2, P3, P4> = SecondaryKeyComposition4(first, second, third, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition3<P2, P3, P4>,
): MainSecondaryKey4<P1, P2, P3, P4> = MainSecondaryKey4(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition3<P2, P3, P4>,
): HashMapStoredCache4<P1, P2, P3, P4> = HashMapStoredCache4(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> SecondaryKeyComposition4<P1, P2, P3, P4>.plus(
    other: KeyPart<P5>,
): SecondaryKeyComposition5<P1, P2, P3, P4, P5> = SecondaryKeyComposition5(first, second, third, fourth, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition4<P2, P3, P4, P5>,
): MainSecondaryKey5<P1, P2, P3, P4, P5> = MainSecondaryKey5(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition4<P2, P3, P4, P5>,
): HashMapStoredCache5<P1, P2, P3, P4, P5> = HashMapStoredCache5(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> SecondaryKeyComposition5<P1, P2, P3, P4, P5>.plus(
    other: KeyPart<P6>,
): Nothing = throw UnsupportedOperationException("Secondary key composition already supports up to 5 parameters")

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition5<P2, P3, P4, P5, P6>,
): MainSecondaryKey6<P1, P2, P3, P4, P5, P6> = MainSecondaryKey6(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition5<P2, P3, P4, P5, P6>,
): HashMapStoredCache6<P1, P2, P3, P4, P5, P6> = HashMapStoredCache6(name, key + secondary)
