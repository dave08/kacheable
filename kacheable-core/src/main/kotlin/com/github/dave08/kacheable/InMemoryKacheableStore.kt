package com.github.dave08.kacheable

import kotlin.time.Duration

class InMemoryKacheableStore(
    val map: MutableMap<String, String> = mutableMapOf(),
    val hashMap: MutableMap<String, MutableMap<String, String>> = mutableMapOf(),
) : KacheableStore {
    override suspend fun delete(key: String) {
        if (!key.contains("*")) {
            map.remove(key)
            hashMap.remove(key)
            return
        }

        val regex = wildcardRegex(key)
        map.keys.filter(regex::matches).toList().forEach(map::remove)
        hashMap.keys.filter(regex::matches).toList().forEach(hashMap::remove)
    }

    override suspend fun deleteHashValue(key: String, field: String) {
        hashMap[key]?.remove(field)
    }

    override suspend fun set(key: String, value: String) {
        map[key] = value
    }

    override suspend fun setHashValue(key: String, field: String, value: String) {
        hashMap.getOrPut(key, ::mutableMapOf)[field] = value
    }

    override suspend fun get(key: String): String? = map[key]

    override suspend fun getHashValue(key: String, field: String): String? = hashMap[key]?.get(field)

    override suspend fun setExpire(key: String, expiry: Duration) {
        // No-op
    }

    private fun wildcardRegex(pattern: String): Regex =
        Regex("^${pattern.split("*").joinToString(".*") { Regex.escape(it) }}$")
}
