package com.github.dave08

import com.github.dave08.kacheable.InMemoryKacheableStore
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.DefaultGetNameStrategy
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.BlockingKacheableStore

class SuspendCacheFixture(
    val store: InMemoryKacheableStore = InMemoryKacheableStore(),
    getNameStrategy: GetNameStrategy = DefaultGetNameStrategy,
) {
    val cache = Kacheable(store, getNameStrategy = getNameStrategy)
}

class InMemoryBlockingKacheableStore(
    val map: MutableMap<String, String> = mutableMapOf(),
) : BlockingKacheableStore {
    override fun delete(key: String) {
        map.remove(key)
    }

    override fun set(key: String, value: String) {
        map[key] = value
    }

    override fun get(key: String): String? = map[key]

    override fun setExpire(key: String, expiry: kotlin.time.Duration) = Unit
}

class BlockingCacheFixture(
    val store: InMemoryBlockingKacheableStore = InMemoryBlockingKacheableStore(),
    getNameStrategy: GetNameStrategy = DefaultGetNameStrategy,
) {
    val cache = BlockingKacheable(store, getNameStrategy = getNameStrategy)
}
