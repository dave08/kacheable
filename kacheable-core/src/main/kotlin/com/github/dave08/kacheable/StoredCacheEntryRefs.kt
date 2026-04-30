@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
fun CacheStorage.storageLayoutOrNull(): CacheStorageLayout? =
    when (this) {
        CacheStorage.String -> CacheStorageLayout.StringValue
        CacheStorage.HashMap -> CacheStorageLayout.HashValue
        CacheStorage.Set -> null
    }

@ExperimentalKacheableApi
interface StoredCacheEntryRef<S : CacheStorage> {
    val name: String
    val cacheArgs: PrimarySecondaryCacheArgs
    val storage: S
    val storageLayout: CacheStorageLayout?
        get() = storage.storageLayoutOrNull()
}

@ExperimentalKacheableApi
interface StoredCachePartRef<S : CacheStorage> : CacheEntryPartRef {
    val storage: S
    override val storageLayout: CacheStorageLayout?
        get() = storage.storageLayoutOrNull()
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
typealias StringCacheEntryRef = CacheEntryRef<CacheStorage.String>

@ExperimentalKacheableApi
typealias HashMapCacheEntryRef = CacheEntryRef<CacheStorage.HashMap>

@ExperimentalKacheableApi
typealias SetMembershipCacheEntryRef = CacheEntryRef<CacheStorage.Set>

@ExperimentalKacheableApi
typealias StringCachePartRef = CachePartRef<CacheStorage.String>

@ExperimentalKacheableApi
typealias HashMapCachePartRef = CachePartRef<CacheStorage.HashMap>

@ExperimentalKacheableApi
typealias SetMembershipCachePartRef = CachePartRef<CacheStorage.Set>
