package com.github.dave08.kacheable

import kotlin.time.Duration

class InMemoryKacheableStore(
    val map: MutableMap<String, String> = mutableMapOf()
) : KacheableStore {
    override suspend fun delete(key: String) {
        if (!key.contains("*")) {
            map.remove(key)
            return
        }

        val regex = wildcardRegex(key)
        map.keys.filter(regex::matches).toList().forEach(map::remove)
    }

    override suspend fun set(key: String, value: String) {
        map[key] = value
    }

    override suspend fun get(key: String): String? = map[key]

    override suspend fun setExpire(key: String, expiry: Duration) {
        // No-op
    }

    private fun wildcardRegex(pattern: String): Regex =
        Regex("^${pattern.split("*").joinToString(".*") { Regex.escape(it) }}$")
}
