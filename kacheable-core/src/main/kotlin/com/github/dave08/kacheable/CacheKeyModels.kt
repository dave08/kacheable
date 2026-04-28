@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
data class PrimarySecondaryCacheArgs(
    val primary: CacheArgs,
    val secondary: CacheArgs? = null,
) {
    val flattened: CacheArgs = secondary?.let { joinArgs(primary, it) } ?: primary
}

@ExperimentalKacheableApi
interface CacheEntryPartRef {
    val name: String
    val args: CacheArgs
    val storageLayout: CacheStorageLayout?
    val cacheArgs: PrimarySecondaryCacheArgs
        get() = PrimarySecondaryCacheArgs(args)
}

@ExperimentalKacheableApi
interface KeyPart<P1 : Any> {
    fun encode(value: P1): CacheArgs
    val wildcardArgs: CacheArgs
}

@ExperimentalKacheableApi
internal data class SimpleCacheEntryPartRef(
    override val name: String,
    override val args: CacheArgs,
    override val storageLayout: CacheStorageLayout? = null,
    override val cacheArgs: PrimarySecondaryCacheArgs = PrimarySecondaryCacheArgs(args),
) : CacheEntryPartRef

@PublishedApi
internal data class SimpleSecondaryKeyPart<P1 : Any>(
    private val encoders: List<(P1) -> Any>,
    override val wildcardArgs: CacheArgs,
) : KeyPart<P1> {
    override fun encode(value: P1): CacheArgs = argsOf(*encoders.map { it(value) }.toTypedArray())
}

@PublishedApi
internal data class RawKeyPart(
    override val wildcardArgs: CacheArgs,
) : KeyPart<CacheArgs> {
    override fun encode(value: CacheArgs): CacheArgs = value
}

@PublishedApi
internal fun groupedEntryPartRef(
    name: String,
    primaryArgs: CacheArgs,
    secondaryArgs: CacheArgs,
    storageLayout: CacheStorageLayout? = null,
): CacheEntryPartRef = SimpleCacheEntryPartRef(
    name = name,
    args = joinArgs(primaryArgs, secondaryArgs),
    storageLayout = storageLayout,
    cacheArgs = PrimarySecondaryCacheArgs(primary = primaryArgs, secondary = secondaryArgs),
)
