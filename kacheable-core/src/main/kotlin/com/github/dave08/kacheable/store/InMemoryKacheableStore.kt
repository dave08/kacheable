package com.github.dave08.kacheable.store

import kotlin.time.Duration

class InMemoryKacheableStore(
    val map: MutableMap<String, String> = mutableMapOf(),
    val hashMap: MutableMap<String, MutableMap<String, String>> = mutableMapOf(),
    val sets: MutableMap<String, MutableSet<String>> = mutableMapOf(),
) : KacheableStore {
    override suspend fun delete(key: String) {
        if (!key.contains("*")) {
            map.remove(key)
            hashMap.remove(key)
            sets.remove(key)
            return
        }

        val regex = wildcardRegex(key)
        map.keys.filter(regex::matches).toList().forEach(map::remove)
        hashMap.keys.filter(regex::matches).toList().forEach(hashMap::remove)
        sets.keys.filter(regex::matches).toList().forEach(sets::remove)
    }

    override suspend fun deleteHashValue(key: String, field: String) {
        hashMap[key]?.remove(field)
    }

    override suspend fun deleteSetMember(key: String, member: String) {
        sets[key]?.remove(member)
    }

    override suspend fun set(key: String, value: String) {
        map[key] = value
    }

    override suspend fun setHashValue(key: String, field: String, value: String) {
        hashMap.getOrPut(key, ::mutableMapOf)[field] = value
    }

    override suspend fun addSetMember(key: String, member: String) {
        sets.getOrPut(key, ::mutableSetOf) += member
    }

    override suspend fun get(key: String): String? = map[key]

    override suspend fun getHashValue(key: String, field: String): String? = hashMap[key]?.get(field)

    override suspend fun isSetMember(key: String, member: String): Boolean = sets[key]?.contains(member) == true

    override suspend fun setExpire(key: String, expiry: Duration) = Unit

    private fun wildcardRegex(pattern: String): Regex =
        Regex("^${pattern.split("*").joinToString(".*") { Regex.escape(it) }}$")
}
