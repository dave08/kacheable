package com.github.dave08.kacheable.redis

/** Physical namespace owned exclusively by generation-checked hash operations. */
internal const val VERSIONED_HASH_NAMESPACE = "__kacheable:versioned-hash:v1:"

internal fun isVersionedRedisKey(key: String): Boolean = key.startsWith(VERSIONED_HASH_NAMESPACE)

internal fun ordinaryRedisKey(key: String): String {
    require(!isVersionedRedisKey(key)) { "The versioned hash namespace is reserved for partition operations." }
    return key
}

internal fun versionedRedisKey(logicalKey: String): String = VERSIONED_HASH_NAMESPACE + ordinaryRedisKey(logicalKey)
