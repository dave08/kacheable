package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.*

val CacheManyResultSpec by testSuite {
    test("omitted batch keys receive nullable failure fallbacks without caching those fallbacks") {
        val cache = Kacheable(InMemoryKacheableStore())
        val key = cacheKey("many-null-fallback", returns<String?>(), exact(keyPart<Int>("id")))
        val failedKeys = mutableListOf<Any?>()
        val result = cache(key.many(1, 2), missPolicy = CacheMissPolicy.load { error ->
            failedKeys += assertIs<UnresolvedCacheKeyException>(error).key
            null
        }) { _, _ -> mapOf(1 to "one") }
        assertEquals(mapOf(1 to "one", 2 to null), result)
        assertEquals(listOf<Any?>(2), failedKeys)
        var loaded = emptyList<Int>()
        cache(key.many(1, 2)) { keys, _ -> loaded = keys; mapOf(2 to "two") }
        assertEquals(listOf(2), loaded)
    }

    test("unrequested loader results are rejected before publication") {
        val store = InMemoryKacheableStore()
        val cache = Kacheable(store)
        val key = cacheKey("many-extra", returns<String>(), exact(keyPart<Int>("id")))
        assertFailsWith<IllegalArgumentException> {
            cache(key.many(1)) { _, _ -> mapOf(1 to "one", 2 to "unrequested") }
        }
        assertTrue(store.map.isEmpty())
    }

    test("mapped logical keys share physical loads and preserve requested aliases in the result") {
        val cache = Kacheable(InMemoryKacheableStore())
        val key = cacheKey("many-alias", returns<String>(), exact(keyPart<ManyLookup>("id", ManyLookup::id)))
        val first = ManyLookup(1, "first")
        val alias = ManyLookup(1, "alias")
        var loaded = emptyList<ManyLookup>()
        val result = cache(key.many(first, alias)) { keys, _ -> loaded = keys; keys.associateWith { "one" } }
        assertEquals(listOf(first), loaded)
        assertEquals(mapOf(first to "one", alias to "one"), result)
    }

    test("no-op selections return partial results with the same explicit-null convention") {
        val cache = KacheableNoOp()
        val key = cacheKey("noop-many", returns<String?>(), exact(keyPart<Int>("id")))
        val result = cache(key.many(3, 2, 1, 3)) { keys, context ->
            assertEquals(listOf(3, 2, 1), keys)
            assertEquals(CacheEntry.Missing, context.entry(3))
            mapOf(1 to "one", 3 to null)
        }
        assertEquals(listOf(3, 1), result.keys.toList())
        assertTrue(result.containsKey(3))
        assertFalse(result.containsKey(2))
    }

    test("a nullable logical key remains the first representative of its physical entry") {
        val cache = Kacheable(InMemoryKacheableStore())
        val key = cacheKey("many-null-key", returns<String>(), exact(keyPart<String?>("id", { "shared" })))
        var loaded = emptyList<String?>()
        val result = cache(key.many(null, "alias")) { keys, _ ->
            loaded = keys
            keys.associateWith { "shared-value" }
        }
        assertEquals(listOf<String?>(null), loaded)
        assertEquals(mapOf(null to "shared-value", "alias" to "shared-value"), result)
    }
}

private data class ManyLookup(val id: Int, val description: String)
