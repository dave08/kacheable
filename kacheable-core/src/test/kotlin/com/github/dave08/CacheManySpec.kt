package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.*

val CacheManySpec by testSuite {
    test("delegating cache shares scalar publications with selected loading") {
        val delegate = Kacheable(InMemoryKacheableStore())
        val cache: Kacheable = DelegatingKacheable(delegate)
        val songs = cacheKey("delegating-scalar-many", returns<String>(), exact(keyPart<Int>("id")))
        cache(songs(2)) { "two" }
        var loaded = emptyList<Int>()

        val result = cache(songs.many(1, 2)) { keys, _ ->
            loaded = keys
            mapOf(1 to "one")
        }

        assertEquals(listOf(1), loaded)
        assertEquals(mapOf(1 to "one", 2 to "two"), result)
    }

    test("delegating cache shares selected publications with scalar loading") {
        val delegate = Kacheable(InMemoryKacheableStore())
        val cache: Kacheable = DelegatingKacheable(delegate)
        val songs = cacheKey("delegating-many-scalar", returns<String>(), exact(keyPart<Int>("id")))

        assertEquals(
            mapOf(1 to "one", 2 to "two"),
            cache(songs.many(1, 2)) { keys, _ -> keys.associateWith { if (it == 1) "one" else "two" } },
        )
        assertEquals("two", cache(songs(2)) { error("selected publication was not delegated") })
    }

    test("selected exact entries load only misses and reuse newly resolved entries in scalar calls") {
        val cache = Kacheable(InMemoryKacheableStore())
        val songs = cacheKey("many-songs", returns<String>(), exact(keyPart<Int>("id")))
        cache(songs(2)) { "two" }
        var loaded = emptyList<Int>()

        val result = cache(songs.many(3, 2, 3, 1)) { keys, _ ->
            loaded = keys
            mapOf(3 to "three", 1 to "one")
        }

        assertEquals(listOf(3, 1), loaded)
        assertEquals(listOf(3, 2, 1), result.keys.toList())
        assertEquals(mapOf(1 to "one", 2 to "two", 3 to "three"), result)
        assertEquals("three", cache(songs(3)) { error("new entry was not stored") })
    }

    test("partial loads retain successes and cached nulls while omitted keys remain unresolved") {
        val songs = cacheKey("partial-songs", returns<String?>(), exact(keyPart<Int>("id")))
        val cache = Kacheable(InMemoryKacheableStore(), configs = mapOf(
            "partial-songs" to CacheConfig("partial-songs", nullPlaceholder = "<null>"),
        ))
        val partial = cache(songs.many(1, 2, 3)) { _, _ -> mapOf(1 to "one", 2 to null) }
        var nextLoad = emptyList<Int>()

        val complete = cache(songs.many(1, 2, 3)) { keys, _ ->
            nextLoad = keys
            mapOf(3 to "three")
        }

        assertTrue(partial.containsKey(2))
        assertFalse(partial.containsKey(3))
        assertEquals(listOf(3), nextLoad)
        assertEquals(mapOf(1 to "one", 2 to null, 3 to "three"), complete)
    }

    test("an empty selection never invokes its loader") {
        val cache = Kacheable(InMemoryKacheableStore())
        val songs = cacheKey("empty-songs", returns<String>(), exact(keyPart<Int>("id")))
        assertEquals(emptyMap(), cache(songs.many(emptyList())) { _, _ -> error("empty loader") })
    }

    test("selected hash values share entries and expose lazy sibling reads without enumeration") {
        val cache = Kacheable(InMemoryKacheableStore())
        val values = cacheKey("many-hash", returns<String>(), partitioned(keyPart<Int>("owner"), keyPart<Int>("id")))
        cache(values(8, 2)) { "two" }

        val result = cache(values.many(8, listOf(1, 2))) { keys, context ->
            assertEquals(listOf(1), keys)
            assertEquals(CacheEntry.Present("two"), context.entry(2))
            mapOf(1 to "one")
        }

        assertEquals(mapOf(1 to "one", 2 to "two"), result)
        assertEquals("one", cache(values(8, 1)) { error("hash entry not shared") })
    }

    test("selected Boolean membership preserves false hits and distinguishes unknown members") {
        val cache = Kacheable(InMemoryKacheableStore())
        val member = cacheKey("many-members", returns<Boolean>(), partitioned(key = keyPart<Int>("id")))
        cache(member(1)) { false }
        var loaded = emptyList<Int>()
        val result = cache(member.many(1, 2, 3)) { keys, _ ->
            loaded = keys
            mapOf(2 to true)
        }
        assertEquals(listOf(2, 3), loaded)
        assertEquals(mapOf(1 to false, 2 to true), result)
        assertEquals(true, cache(member(2)) { error("membership not shared") })
    }
}

private class DelegatingKacheable(cache: Kacheable) : Kacheable by cache
