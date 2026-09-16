package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.*
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val PartitionContextSpec by testSuite {
    testFixture { PartitionFixture() } asContextForEach {
        test("published contextual entries return stored values without invoking the loader") {
            assertEquals("one", cache.cache(pages("a", 1)) { _, _ -> "one" })
            assertEquals("one", cache.cache(pages("a", 1)) { _, _ -> error("unexpected loader") })
        }
        test("selected contextual entries expose only requested siblings") {
            cache.cache(pages("a", 1)) { _, _ -> "one" }
            cache.cache(pages("a", 2)) { _, _ -> "two" }
            cache.cache(pages("a", 3)) { _, context ->
                assertEquals(mapOf(1 to "one"), context.entries(listOf(1, 9)))
                "three"
            }
        }
        test("a saved partition context cannot be used after its loader finishes") {
            lateinit var escaped: CachePartitionContext<Int, String, EnumerableKeyPart<Int>>
            cache.cache(pages("a", 1)) { _, context ->
                escaped = context
                "one"
            }
            assertFailsWith<IllegalStateException> { escaped.entries() }
        }
        test("generation changes replay the loader with a fresh context") {
            var loads = 0
            val result = cache.cache(pages("a", 1)) { _, context ->
                loads++
                if (loads == 1) cache.invalidate(pages.partition("a"))
                context.entries()
                "attempt-$loads"
            }
            assertEquals("attempt-2", result)
        }
        test("cancelled contextual loaders are never replayed") {
            var loads = 0
            assertFailsWith<CancellationException> {
                cache.cache(pages("a", 1)) { _, _ ->
                    loads++
                    throw CancellationException("stop")
                }
            }
            assertEquals(1, loads)
        }
    }
    test("partition coordination returns each concurrent entry's own result") {
        val fixture = PartitionFixture(partition = CachePartitionPolicy.OnDemand(publication = CachePublication.IfAbsent, coordination = CacheCoordination.Partition))
        coroutineScope {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache.cache(fixture.pages("a", 1)) { key, _ ->
                    entered.complete(Unit)
                    release.await()
                    "page-$key"
                }
            }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache.cache(fixture.pages("a", 2)) { key, _ -> "page-$key" }
            }
            release.complete(Unit)
            assertEquals("page-1", first.await())
            assertEquals("page-2", second.await())
        }
    }
    test("sequential loading exposes each previously published predecessor") {
        val fixture = PartitionFixture(partition = CachePartitionPolicy.SequentialFrom())
        val loaded = mutableListOf<Int>()
        val result = fixture.cache.cache(fixture.pages("a", 3)) { key, context ->
            loaded += key
            if (key == 0) "0" else (context.entry(key - 1) as CachePartitionEntry.Present).value + key
        }
        assertEquals("0123", result)
        assertEquals(listOf(0, 1, 2, 3), loaded)
    }
    test("contextual loading uses the configured loader timeout") {
        val fixture = PartitionFixture(resilience = CacheResilienceConfig(loadTimeout = 50.milliseconds))
        assertFailsWith<TimeoutCancellationException> {
            fixture.cache.cache(fixture.pages("a", 1)) { _, _ -> awaitCancellation() }
        }
    }
    test("context reads fetch selected fields without eagerly scanning the hash") {
        val real = InMemoryKacheableStore()
        val reads = mutableListOf<List<String>?>()
        val recording = object : KacheableStore by real, VersionedHashOperations by real {
            override suspend fun readHash(key: String, version: HashVersion, fields: List<String>?): Map<String, String>? {
                reads += fields
                return real.readHash(key, version, fields)
            }
        }
        val fixture = PartitionFixture(store = recording)
        fixture.cache.cache(fixture.pages("a", 1)) { _, context ->
            reads.clear()
            assertEquals(emptyMap(), context.entries(listOf(8, 9)))
            assertEquals(listOf<List<String>?>(listOf("8", "9")), reads)
            "one"
        }
    }
    test("expiry during a loader replays its attempt in a new generation") {
        var now = 0L
        val fixture = PartitionFixture(store = InMemoryKacheableStore(nanoTime = { now }), expiry = 1.seconds)
        var attempts = 0
        val result = fixture.cache.cache(fixture.pages("a", 1)) { _, _ ->
            attempts++
            if (attempts == 1) now += 2.seconds.inWholeNanoseconds
            "attempt-$attempts"
        }
        assertEquals("attempt-2", result)
    }
    test("revision changes replay a dependent loader against the new siblings") {
        val fixture = PartitionFixture()
        var attempts = 0
        val result = fixture.cache.cache(fixture.pages("a", 2)) { _, context ->
            attempts++
            val before = context.entry(1)
            if (attempts == 1) fixture.cache.cache(fixture.pages("a", 1)) { _, _ -> "winner" }
            when (before) {
                CachePartitionEntry.Missing -> "stale"
                is CachePartitionEntry.Present -> before.value
            }
        }
        assertEquals("winner", result)
        assertEquals(2, attempts)
    }
    test("guarded loading obeys the shared cache load admission limit") {
        val fixture = PartitionFixture(resilience = CacheResilienceConfig(maxConcurrentLoads = 1))
        coroutineScope {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var secondEntered = false
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache.cache(fixture.pages("a", 1)) { _, _ ->
                    entered.complete(Unit)
                    release.await()
                    "one"
                }
            }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache.cache(fixture.pages("b", 1)) { _, _ ->
                    secondEntered = true
                    "two"
                }
            }
            assertEquals(false, secondEntered)
            release.complete(Unit)
            first.await()
            assertEquals("two", second.await())
        }
    }
    test("sequential loading restarts its prerequisites after generation replacement") {
        val fixture = PartitionFixture(partition = CachePartitionPolicy.SequentialFrom())
        val loaded = mutableListOf<Int>()
        var replaced = false
        val result = fixture.cache.cache(fixture.pages("a", 2)) { key, context ->
            loaded += key
            if (key == 1 && !replaced) {
                replaced = true
                fixture.cache.invalidate(fixture.pages.partition("a"))
            }
            if (key == 0) "0" else (context.entry(key - 1) as CachePartitionEntry.Present).value + key
        }
        assertEquals("012", result)
        assertEquals(listOf(0, 1, 0, 1, 2), loaded)
    }
    test("sequential loading stops when a prerequisite is not published") {
        val fixture = PartitionFixture(partition = CachePartitionPolicy.SequentialFrom())
        val loaded = mutableListOf<Int>()
        assertFailsWith<IllegalArgumentException> {
            fixture.cache.cache(fixture.pages("a", 2), cacheIf = { false }) { key, _ ->
                loaded += key
                "$key"
            }
        }
        assertEquals(listOf(0), loaded)
    }
    test("continuously invalidated partition loads stop at their configured retry bound") {
        val fixture = PartitionFixture()
        var attempts = 0
        assertFailsWith<IllegalStateException> {
            fixture.cache.cache(fixture.pages("a", 1)) { _, _ ->
                attempts++
                fixture.cache.invalidate(fixture.pages.partition("a"))
                "stale"
            }
        }
        assertEquals(3, attempts)
    }
    test("concurrent immutable publication returns the published winner to both callers") {
        val fixture = PartitionFixture()
        coroutineScope {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val loser = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache.cache(fixture.pages("a", 1)) { _, _ ->
                    started.complete(Unit)
                    release.await()
                    "loser"
                }
            }
            started.await()
            assertEquals("winner", fixture.cache.cache(fixture.pages("a", 1)) { _, _ -> "winner" })
            release.complete(Unit)
            assertEquals("winner", loser.await())
        }
    }
    test("generation-checked Replace caches reject entry invalidation before any entry exists") {
        val fixture = PartitionFixture(partition = CachePartitionPolicy.OnDemand())
        assertFailsWith<IllegalArgumentException> {
            fixture.cache.invalidate(fixture.pages("a", 1))
        }
    }
    test("generation-checked Replace caches reject matching invalidation before any entry exists") {
        val fixture = PartitionFixture(partition = CachePartitionPolicy.OnDemand())
        val page = matchableKeyPart<Int>("page")
        val pages = cacheKey("pages", returns<String>(), key = partitioned(partition = keyPart<String>("group"), key = page))
        assertFailsWith<IllegalArgumentException> {
            fixture.cache.invalidate(pages.matching("a", page(1)))
        }
    }
    test("guarded null results without a placeholder remain cache misses") {
        val fixture = PartitionFixture()
        val nullable = cacheKey(
            "pages",
            returns<String?>(),
            key = partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")),
        )
        var loads = 0
        repeat(2) {
            fixture.cache.cache(nullable("a", 1)) { _, _ ->
                loads++
                null
            }
        }
        assertEquals(2, loads)
    }
    test("sequential loading rejects an uncached nullable prerequisite") {
        val fixture = PartitionFixture(partition = CachePartitionPolicy.SequentialFrom())
        val nullable = cacheKey(
            "pages",
            returns<String?>(),
            key = partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")),
        )
        val loads = mutableListOf<Int>()
        assertFailsWith<IllegalArgumentException> {
            fixture.cache.cache(nullable("a", 1)) { key, _ ->
                loads += key
                null
            }
        }
        assertEquals(listOf(0), loads)
    }
    test("nullable partition entries distinguish cached null from absence") {
        val fixture = PartitionFixture(nullPlaceholder = "__NULL__")
        val nullable = cacheKey(
            "pages",
            returns<String?>(),
            key = partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")),
        )
        fixture.cache.cache(nullable("a", 1)) { _, _ -> null }
        fixture.cache.cache(nullable("a", 2)) { _, context ->
            assertEquals(CachePartitionEntry.Present(null), context.entry(1))
            assertEquals(CachePartitionEntry.Missing, context.entry(9))
            "two"
        }
    }
    test("guarded zero argument loader preserves a cached null") {
        val fixture = PartitionFixture(nullPlaceholder = "__NULL__")
        val nullable = cacheKey(
            "pages",
            returns<String?>(),
            key = partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")),
        )
        assertEquals(null, fixture.cache.cache(nullable("a", 1), block = { null }))
        assertEquals(null, fixture.cache.cache(nullable("a", 1), block = { error("Cached null must not invoke the loader") }))
    }

}

private class PartitionFixture(
    partition: CachePartitionPolicy = CachePartitionPolicy.OnDemand(publication = CachePublication.IfAbsent),
    resilience: CacheResilienceConfig? = null,
    val store: KacheableStore = InMemoryKacheableStore(),
    expiry: kotlin.time.Duration? = null,
    nullPlaceholder: String? = null,
) {
    val cache = Kacheable(
        store,
        configs = mapOf(
            "pages" to CacheConfig(
                "pages",
                partition = partition,
                resilience = resilience,
                nullPlaceholder = nullPlaceholder,
                expiry = expiry ?: kotlin.time.Duration.INFINITE,
                expiryType = if (expiry == null) ExpiryType.none else ExpiryType.after_write,
            ),
        ),
    )
    val pages = cacheKey(
        "pages",
        returns<String>(),
        key = partitioned(
            partition = keyPart<String>("group"),
            key = enumerableKeyPart<Int>("page"),
        ),
    )
}
