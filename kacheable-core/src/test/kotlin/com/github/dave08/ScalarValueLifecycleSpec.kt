package com.github.dave08

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.cache
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull

val ScalarValueLifecycleSpec by testSuite {
    test("blocking ordinary cache treats a stored null placeholder as a cache hit") {
        val store = InMemoryBlockingKacheableStore()
        val cacheName = "blocking-null-hit"
        val cache = BlockingKacheable(
            store,
            configs = mapOf(cacheName to CacheConfig(cacheName, nullPlaceholder = "<null>")),
        )
        val nullableValue = cacheKey(cacheName, returns<String?>(), exact(keyPart<Int>("id")))
        var loads = 0

        val first = cache.cache(nullableValue(1)) {
            loads++
            null
        }
        val second = cache.cache(nullableValue(1)) {
            loads++
            "unexpected"
        }

        assertNull(first)
        assertNull(second)
        assertEquals(1, loads)
    }

    test("configured null placeholder still respects suspending storeResultIf") {
        val store = InMemoryKacheableStore()
        val cacheName = "suspending-rejected-null"
        val cache = Kacheable(
            store,
            configs = mapOf(cacheName to CacheConfig(cacheName, nullPlaceholder = "<null>")),
        )
        val nullableValue = cacheKey(cacheName, returns<String?>(), exact(keyPart<Int>("id")))

        val result = cache.cache(
            nullableValue(1),
            missPolicy = CacheMissPolicy.load(),
            refreshPolicy = CacheRefreshPolicy.neverRefresh(),
            storeResultIf = { false },
        ) { null }

        assertNull(result)
        assertNull(store.map["$cacheName:1"])
    }

    test("configured null placeholder still respects blocking cacheIf") {
        val store = InMemoryBlockingKacheableStore()
        val cacheName = "blocking-rejected-null"
        val cache = BlockingKacheable(
            store,
            configs = mapOf(cacheName to CacheConfig(cacheName, nullPlaceholder = "<null>")),
        )
        val nullableValue = cacheKey(cacheName, returns<String?>(), exact(keyPart<Int>("id")))

        val result = cache.cache(nullableValue(1), cacheIf = { false }) { null }

        assertNull(result)
        assertNull(store.map["$cacheName:1"])
    }

    test("nullable failure fallback may deliberately return null") {
        val cacheName = "nullable-failure-fallback"
        val cache = Kacheable(InMemoryKacheableStore())
        val nullableValue = cacheKey(cacheName, returns<String?>(), exact(keyPart<Int>("id")))

        val result = cache.cache(
            nullableValue(1),
            missPolicy = CacheMissPolicy.load(fallbackOnFailure = { null }),
        ) {
            error("load failed")
        }

        assertNull(result)
    }
}
