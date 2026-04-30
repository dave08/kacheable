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

@ExperimentalKacheableApi
@Deprecated(
    message = "Use CacheEntryRef<CacheStorage.String> instead.",
    replaceWith = ReplaceWith("CacheEntryRef<CacheStorage.String>", imports = ["com.github.dave08.kacheable.CacheEntryRef", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias StringCacheEntryRef = CacheEntryRef<CacheStorage.String>

@ExperimentalKacheableApi
@Deprecated(
    message = "Use CacheEntryRef<CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("CacheEntryRef<CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.CacheEntryRef", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapCacheEntryRef = CacheEntryRef<CacheStorage.HashMap>

@ExperimentalKacheableApi
@Deprecated(
    message = "Use CacheEntryRef<CacheStorage.Set> instead.",
    replaceWith = ReplaceWith("CacheEntryRef<CacheStorage.Set>", imports = ["com.github.dave08.kacheable.CacheEntryRef", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias SetMembershipCacheEntryRef = CacheEntryRef<CacheStorage.Set>

@ExperimentalKacheableApi
@Deprecated(
    message = "Use CachePartRef<CacheStorage.String> instead.",
    replaceWith = ReplaceWith("CachePartRef<CacheStorage.String>", imports = ["com.github.dave08.kacheable.CachePartRef", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias StringCachePartRef = CachePartRef<CacheStorage.String>

@ExperimentalKacheableApi
@Deprecated(
    message = "Use CachePartRef<CacheStorage.HashMap> instead.",
    replaceWith = ReplaceWith("CachePartRef<CacheStorage.HashMap>", imports = ["com.github.dave08.kacheable.CachePartRef", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias HashMapCachePartRef = CachePartRef<CacheStorage.HashMap>

@ExperimentalKacheableApi
@Deprecated(
    message = "Use CachePartRef<CacheStorage.Set> instead.",
    replaceWith = ReplaceWith("CachePartRef<CacheStorage.Set>", imports = ["com.github.dave08.kacheable.CachePartRef", "com.github.dave08.kacheable.CacheStorage"]),
)
typealias SetMembershipCachePartRef = CachePartRef<CacheStorage.Set>
