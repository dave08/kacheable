package com.github.dave08

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheEntry
import com.github.dave08.kacheable.CachePartitionPolicy
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.BlockingKacheableNoOp
import com.github.dave08.kacheable.blocking.BlockingCacheLoadContext
import com.github.dave08.kacheable.blocking.cache
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingStoreMutationScope
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

val BlockingCacheManySpec by testSuite {
    test("delegating blocking cache shares scalar publications with selected loading") {
        val delegate = BlockingKacheable(InMemoryBlockingKacheableStore())
        val cache: BlockingKacheable = DelegatingBlockingKacheable(delegate)
        val songs = cacheKey("blocking-delegating-scalar-many", returns<String>(), exact(keyPart<Int>("id")))
        cache(songs(2)) { "two" }
        var loaded = emptyList<Int>()

        val result = cache(songs.many(1, 2)) { keys, _ ->
            loaded = keys
            mapOf(1 to "one")
        }

        assertEquals(listOf(1), loaded)
        assertEquals(mapOf(1 to "one", 2 to "two"), result)
    }

    test("delegating blocking cache shares selected publications with scalar loading") {
        val delegate = BlockingKacheable(InMemoryBlockingKacheableStore())
        val cache: BlockingKacheable = DelegatingBlockingKacheable(delegate)
        val songs = cacheKey("blocking-delegating-many-scalar", returns<String>(), exact(keyPart<Int>("id")))

        assertEquals(
            mapOf(1 to "one", 2 to "two"),
            cache(songs.many(1, 2)) { keys, _ -> keys.associateWith { if (it == 1) "one" else "two" } },
        )
        assertEquals("two", cache(songs(2)) { error("selected publication was not delegated") })
    }

    test("ordinary blocking selections ignore coroutine resilience just like scalar calls") {
        val cache = BlockingKacheable(
            InMemoryBlockingKacheableStore(),
            configs = mapOf("blocking-many-resilience" to CacheConfig(
                "blocking-many-resilience",
                resilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Redis),
            )),
        )
        val values = cacheKey("blocking-many-resilience", returns<String>(), exact(keyPart<Int>("id")))
        assertEquals("one", cache(values(1)) { "one" })

        val result = cache(values.many(1, 2)) { keys, _ -> keys.associateWith { "two" } }

        assertEquals(mapOf(1 to "one", 2 to "two"), result)
    }

    test("blocking selected exact entries load only misses and share published values with scalar calls") {
        val store = RecordingMutationBlockingStore()
        val cache = BlockingKacheable(store)
        val songs = cacheKey("blocking-many-songs", returns<String>(), exact(keyPart<Int>("id")))
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
        assertEquals(1, store.mutationCalls)
    }

    test("blocking selected hash entries expose cached siblings to the loader context") {
        val cache = BlockingKacheable(InMemoryBlockingKacheableStore())
        val values = cacheKey(
            "blocking-many-hash",
            returns<String>(),
            partitioned(keyPart<Int>("owner"), keyPart<Int>("id")),
        )
        cache(values(8, 2)) { "two" }

        val result = cache(values.many(8, listOf(1, 2))) { keys, context ->
            assertEquals(listOf(1), keys)
            assertEquals(CacheEntry.Present("two"), context.entry(2))
            mapOf(1 to "one")
        }

        assertEquals(mapOf(1 to "one", 2 to "two"), result)
    }

    test("blocking selected membership entries preserve cached false values and unknown misses") {
        val cache = BlockingKacheable(InMemoryBlockingKacheableStore())
        val member = cacheKey(
            "blocking-many-members",
            returns<Boolean>(),
            partitioned(key = keyPart<Int>("id")),
        )
        cache(member(1)) { false }
        var loaded = emptyList<Int>()

        val result = cache(member.many(1, 2, 3)) { keys, _ ->
            loaded = keys
            mapOf(2 to true)
        }

        assertEquals(listOf(2, 3), loaded)
        assertEquals(mapOf(1 to false, 2 to true), result)
        assertEquals(true, cache(member(2)) { error("membership result was not stored") })
    }

    test("blocking selected enum membership entries preserve cached classifications") {
        val cache = BlockingKacheable(InMemoryBlockingKacheableStore())
        val reaction = cacheKey(
            "blocking-many-reactions",
            returns<SongLike>(),
            partitioned(keyPart<Int>("song"), keyPart<Int>("account")),
        )
        cache(reaction(7, 1)) { SongLike.DISLIKE }
        var loaded = emptyList<Int>()

        val result = cache(reaction.many(7, listOf(1, 2, 3))) { keys, _ ->
            loaded = keys
            mapOf(2 to SongLike.LIKE)
        }

        assertEquals(listOf(2, 3), loaded)
        assertEquals(mapOf(1 to SongLike.DISLIKE, 2 to SongLike.LIKE), result)
        assertEquals(SongLike.LIKE, cache(reaction(7, 2)) { error("classification result was not stored") })
    }

    test("blocking selected exact hits refresh after-access expiry without invoking the loader") {
        val store = InMemoryBlockingKacheableStore()
        val cache = BlockingKacheable(
            store,
            configs = mapOf(
                "blocking-many-access" to CacheConfig(
                    "blocking-many-access",
                    expiryType = ExpiryType.after_access,
                    expiry = 5.minutes,
                ),
            ),
        )
        val values = cacheKey("blocking-many-access", returns<String>(), exact(keyPart<Int>("id")))
        cache(values(1)) { "one" }
        store.expireCalls.clear()

        val result = cache(values.many(1)) { _, _ -> error("cached entry should satisfy the batch") }

        assertEquals(mapOf(1 to "one"), result)
        assertEquals(listOf("blocking-many-access:1" to 5.minutes), store.expireCalls)
    }

    test("blocking selected entry reads preserve cached nulls across partial loads") {
        val store = InMemoryBlockingKacheableStore()
        val cache = BlockingKacheable(
            store,
            configs = mapOf("blocking-many-null" to CacheConfig("blocking-many-null", nullPlaceholder = "<null>")),
        )
        val values = cacheKey("blocking-many-null", returns<String?>(), exact(keyPart<Int>("id")))
        val partial = cache(values.many(1, 2, 3)) { _, _ -> mapOf(1 to "one", 2 to null) }
        var loaded = emptyList<Int>()

        val complete = cache(values.many(1, 2, 3)) { keys, _ ->
            loaded = keys
            mapOf(3 to "three")
        }

        assertTrue(partial.containsKey(2))
        assertFalse(partial.containsKey(3))
        assertEquals(listOf(3), loaded)
        assertEquals(mapOf(1 to "one", 2 to null, 3 to "three"), complete)
    }

    test("blocking selected guarded entries use generation-checked partition publication") {
        val cache = BlockingKacheable(
            BlockingPartitionTestStore(),
            configs = mapOf(
                "blocking-many-guarded" to CacheConfig(
                    "blocking-many-guarded",
                    partition = CachePartitionPolicy.OnDemand(),
                ),
            ),
        )
        val values = cacheKey(
            "blocking-many-guarded",
            returns<String>(),
            partitioned(keyPart<Int>("owner"), keyPart<Int>("id")),
        )

        val first = cache(values.many(8, listOf(1, 2))) { _, _ -> mapOf(1 to "one", 2 to "two") }
        val second = cache(values.many(8, listOf(1, 2))) { _, _ -> error("guarded entries were not published") }

        assertEquals(mapOf(1 to "one", 2 to "two"), first)
        assertEquals(first, second)
    }

    test("blocking no-op selected loads normalize partial nullable results to requested key order") {
        val cache = BlockingKacheableNoOp()
        val values = cacheKey("blocking-many-noop", returns<String?>(), exact(keyPart<Int>("id")))
        var loaded = emptyList<Int>()
        lateinit var escaped: BlockingCacheLoadContext<Int, String?, KeyPart<Int>>

        val result = cache(values.many(3, 2, 3, 1)) { keys, context ->
            loaded = keys
            escaped = context
            assertEquals(CacheEntry.Missing, context.entry(2))
            assertEquals(emptyMap(), context.entries(listOf(1, 2)))
            linkedMapOf(3 to null, 1 to "one")
        }

        assertEquals(listOf(3, 2, 1), loaded)
        assertEquals(listOf(3, 1), result.keys.toList())
        assertTrue(result.containsKey(3))
        assertEquals(mapOf(3 to null, 1 to "one"), result)
        assertFailsWith<IllegalStateException> { escaped.entry(2) }
    }

    test("blocking no-op selected loads reject unrequested loader results") {
        val cache = BlockingKacheableNoOp()
        val values = cacheKey("blocking-many-noop-extra", returns<String>(), exact(keyPart<Int>("id")))

        assertFailsWith<IllegalArgumentException> {
            cache(values.many(1)) { _, _ -> mapOf(1 to "one", 99 to "unrequested") }
        }
    }
}

private class RecordingMutationBlockingStore(
    private val delegate: InMemoryBlockingKacheableStore = InMemoryBlockingKacheableStore(),
) : BlockingKacheableStore by delegate {
    var mutationCalls: Int = 0
        private set

    override fun mutate(block: BlockingStoreMutationScope.() -> Unit) {
        mutationCalls++
        delegate.mutate(block)
    }
}

private class DelegatingBlockingKacheable(cache: BlockingKacheable) : BlockingKacheable by cache
