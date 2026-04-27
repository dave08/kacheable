@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

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
data class SetMembershipCacheEntryRef(
    val name: String,
    val keyGroups: CacheKeyGroups,
)

@ExperimentalKacheableApi
data class SetMembershipCachePartRef(
    val name: String,
    val keyGroups: CacheKeyGroups,
)
