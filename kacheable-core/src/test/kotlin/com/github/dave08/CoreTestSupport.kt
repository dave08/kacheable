package com.github.dave08

import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.defaultCacheNamingStrategy
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import kotlin.time.Duration

class SuspendCacheFixture(
    val store: InMemoryKacheableStore = InMemoryKacheableStore(),
    namingStrategy: CacheNamingStrategy = defaultCacheNamingStrategy(),
) {
    val cache = Kacheable(store, namingStrategy = namingStrategy)
}

class InMemoryBlockingKacheableStore(
    val map: MutableMap<String, String> = mutableMapOf(),
    val hashMap: MutableMap<String, MutableMap<String, String>> = mutableMapOf(),
    val sets: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val expiries: MutableMap<String, Duration> = mutableMapOf(),
    val expireCalls: MutableList<Pair<String, Duration>> = mutableListOf(),
) : BlockingKacheableStore {
    override fun delete(key: String) {
        if (!key.contains("*")) {
            map.remove(key)
            hashMap.remove(key)
            sets.remove(key)
            return
        }

        val regex = Regex("^${key.split("*").joinToString(".*") { Regex.escape(it) }}$")
        map.keys.filter(regex::matches).toList().forEach(map::remove)
        hashMap.keys.filter(regex::matches).toList().forEach(hashMap::remove)
        sets.keys.filter(regex::matches).toList().forEach(sets::remove)
    }

    override fun deleteHashValue(key: String, field: String) {
        hashMap[key]?.remove(field)
    }

    override fun deleteSetMember(key: String, member: String) {
        sets[key]?.remove(member)
    }

    override fun set(key: String, value: String) {
        map[key] = value
    }

    override fun setHashValue(key: String, field: String, value: String) {
        hashMap.getOrPut(key, ::mutableMapOf)[field] = value
    }

    override fun addSetMember(key: String, member: String) {
        sets.getOrPut(key, ::mutableSetOf) += member
    }

    override fun get(key: String): String? = map[key]

    override fun getHashValue(key: String, field: String): String? = hashMap[key]?.get(field)

    override fun isSetMember(key: String, member: String): Boolean = sets[key]?.contains(member) == true

    override fun setExpire(key: String, expiry: Duration) {
        expiries[key] = expiry
        expireCalls += key to expiry
    }
}

class BlockingCacheFixture(
    val store: InMemoryBlockingKacheableStore = InMemoryBlockingKacheableStore(),
    namingStrategy: CacheNamingStrategy = defaultCacheNamingStrategy(),
) {
    val cache = BlockingKacheable(store, namingStrategy = namingStrategy)
}
