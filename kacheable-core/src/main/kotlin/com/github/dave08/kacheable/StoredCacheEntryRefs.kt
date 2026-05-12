package com.github.dave08.kacheable

interface StoredCacheEntryRef<S : CacheStorage> {
    val name: String
    val cacheArgs: PrimarySecondaryCacheArgs
    val storage: S
}

interface StoredCachePartRef<S : CacheStorage> : CacheEntryPartRef {
    override val storage: S
}

interface StoredCacheAllRef<S : CacheStorage> {
    val name: String
    val storage: S
}

data class StoredEntryRef<S : CacheStorage>(
    override val name: String,
    override val cacheArgs: PrimarySecondaryCacheArgs,
    override val storage: S,
) : StoredCacheEntryRef<S>

data class StoredPartRef<S : CacheStorage>(
    override val name: String,
    override val args: CacheArgs,
    override val cacheArgs: PrimarySecondaryCacheArgs,
    override val storage: S,
    override val secondaryPatternPartArgs: List<CacheArgs>? = null,
) : StoredCachePartRef<S>

data class StoredAllRef<S : CacheStorage>(
    override val name: String,
    override val storage: S,
) : StoredCacheAllRef<S>
