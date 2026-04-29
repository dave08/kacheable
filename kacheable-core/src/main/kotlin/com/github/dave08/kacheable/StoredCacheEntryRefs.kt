@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
interface StoredCacheEntryRef<S : CacheStorage> {
    val name: String
    val cacheArgs: PrimarySecondaryCacheArgs
    val storageLayout: CacheStorageLayout
}

@ExperimentalKacheableApi
data class HashMapCacheEntryRef(
    override val name: String,
    override val cacheArgs: PrimarySecondaryCacheArgs,
) : StoredCacheEntryRef<CacheStorage.HashMap> {
    override val storageLayout: CacheStorageLayout = CacheStorageLayout.HashValue
}

@ExperimentalKacheableApi
data class StringCacheEntryRef(
    override val name: String,
    override val cacheArgs: PrimarySecondaryCacheArgs,
) : StoredCacheEntryRef<CacheStorage.String> {
    override val storageLayout: CacheStorageLayout = CacheStorageLayout.StringValue
}

@ExperimentalKacheableApi
data class SetMembershipCacheEntryRef(
    val name: String,
    val cacheArgs: PrimarySecondaryCacheArgs,
)

@ExperimentalKacheableApi
data class SetMembershipCachePartRef(
    val name: String,
    val cacheArgs: PrimarySecondaryCacheArgs,
)
