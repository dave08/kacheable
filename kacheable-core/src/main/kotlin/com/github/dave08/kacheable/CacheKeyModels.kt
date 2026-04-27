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
