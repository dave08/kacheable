@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
interface StoredCacheEntryRef<S : CacheStorage> {
    val name: String
    val cacheArgs: PrimarySecondaryCacheArgs
    val storage: S
}

@ExperimentalKacheableApi
interface StoredCachePartRef<S : CacheStorage> : CacheEntryPartRef {
    override val storage: S
}

@ExperimentalKacheableApi
data class CacheEntryRef<S : CacheStorage>(
    override val name: String,
    override val cacheArgs: PrimarySecondaryCacheArgs,
    override val storage: S,
) : StoredCacheEntryRef<S>

@ExperimentalKacheableApi
data class CachePartRef<S : CacheStorage>(
    override val name: String,
    override val args: CacheArgs,
    override val cacheArgs: PrimarySecondaryCacheArgs,
    override val storage: S,
    override val secondaryPatternPartArgs: List<CacheArgs>? = null,
) : StoredCachePartRef<S>
