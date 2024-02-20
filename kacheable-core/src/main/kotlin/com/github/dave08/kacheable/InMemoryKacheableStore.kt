package com.github.dave08.kacheable

import kotlin.time.Duration

internal class InMemoryKacheableStore(
    val map: MutableMap<String, String> = mutableMapOf()
) : KacheableStore {
    override suspend fun delete(key: String) {
        map.remove(key)
    }

    override suspend fun set(key: String, value: String) {
        map[key] = value
    }

    override suspend fun get(key: String): String? = map[key]

    override suspend fun setExpire(key: String, expiry: Duration) {
        // No-op
    }
}