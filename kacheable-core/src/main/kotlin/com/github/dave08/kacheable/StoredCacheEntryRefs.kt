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
interface StoredCacheAllRef<S : CacheStorage> {
    val name: String
    val storage: S
}

@ExperimentalKacheableApi
data class StoredEntryRef<S : CacheStorage>(
    override val name: String,
    override val cacheArgs: PrimarySecondaryCacheArgs,
    override val storage: S,
) : StoredCacheEntryRef<S>

@ExperimentalKacheableApi
data class StoredPartRef<S : CacheStorage>(
    override val name: String,
    override val args: CacheArgs,
    override val cacheArgs: PrimarySecondaryCacheArgs,
    override val storage: S,
    override val secondaryPatternPartArgs: List<CacheArgs>? = null,
) : StoredCachePartRef<S>

@ExperimentalKacheableApi
data class StoredAllRef<S : CacheStorage>(
    override val name: String,
    override val storage: S,
) : StoredCacheAllRef<S>
