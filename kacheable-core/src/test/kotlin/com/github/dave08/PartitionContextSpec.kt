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
import kotlin.test.assertTrue

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
            lateinit var escaped: CacheLoadContext<Int, String, EnumerableKeyPart<Int>>
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
            if (key == 0) "0" else (context.entry(key - 1) as CacheEntry.Present).value + key
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
                CacheEntry.Missing -> "stale"
                is CacheEntry.Present -> before.value
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
            if (key == 0) "0" else (context.entry(key - 1) as CacheEntry.Present).value + key
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
            assertEquals(CacheEntry.Present(null), context.entry(1))
            assertEquals(CacheEntry.Missing, context.entry(9))
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
    test("guarded selected loading batches misses and retains partial successes") {
        val fixture = PartitionFixture()
        fixture.cache(fixture.pages("a", 2)) { _, _ -> "two" }
        val calls = mutableListOf<List<Int>>()

        val partial = fixture.cache(fixture.pages.many("a", listOf(3, 2, 3, 1, 4))) { keys, _ ->
            calls += keys
            mapOf(3 to "three", 1 to "one")
        }
        val completed = fixture.cache(fixture.pages.many("a", listOf(3, 2, 1, 4))) { keys, _ ->
            calls += keys
            mapOf(4 to "four")
        }

        assertEquals(listOf(listOf(3, 1, 4), listOf(4)), calls)
        assertEquals(listOf(3, 2, 1), partial.keys.toList())
        assertEquals(mapOf(3 to "three", 2 to "two", 1 to "one", 4 to "four"), completed)
    }
    test("guarded selected loading deduplicates mapped fields and reconstructs logical aliases") {
        val cache = Kacheable(
            InMemoryKacheableStore(),
            configs = mapOf("mapped-pages" to CacheConfig(
                "mapped-pages",
                partition = CachePartitionPolicy.OnDemand(publication = CachePublication.IfAbsent),
            )),
        )
        val pages = cacheKey(
            "mapped-pages",
            returns<String>(),
            key = partitioned(
                partition = keyPart<String>("group"),
                key = keyPart<MappedPage>("page", MappedPage::number),
            ),
        )
        val firstAlias = MappedPage(1, "first")
        val secondAlias = MappedPage(1, "second")
        var loaded = emptyList<MappedPage>()

        val result = cache(pages.many("a", listOf(firstAlias, secondAlias))) { keys, _ ->
            loaded = keys
            mapOf(firstAlias to "one")
        }

        assertEquals(listOf(firstAlias), loaded)
        assertEquals(listOf(firstAlias, secondAlias), result.keys.toList())
        assertEquals("one", result[firstAlias])
        assertEquals("one", result[secondAlias])
    }
    test("guarded selected loading preserves a nullable first key that maps to an alias") {
        val cache = Kacheable(
            InMemoryKacheableStore(),
            configs = mapOf("nullable-pages" to CacheConfig(
                "nullable-pages",
                partition = CachePartitionPolicy.OnDemand(publication = CachePublication.IfAbsent),
            )),
        )
        val pages = cacheKey(
            "nullable-pages",
            returns<String>(),
            key = partitioned(
                partition = keyPart<String>("group"),
                key = keyPart<String?>("page", { it ?: "alias" }),
            ),
        )
        var loaded = emptyList<String?>()

        val result = cache(pages.many("a", listOf(null, "alias"))) { keys, _ ->
            loaded = keys
            mapOf<String?, String>(null to "one")
        }

        assertEquals(listOf<String?>(null), loaded)
        assertEquals(listOf<String?>(null, "alias"), result.keys.toList())
        assertEquals("one", result[null])
        assertEquals("one", result["alias"])
    }
    test("guarded loader context entries reconstruct requested aliases including a nullable first key") {
        val cache = Kacheable(
            InMemoryKacheableStore(),
            configs = mapOf("nullable-context-pages" to CacheConfig(
                "nullable-context-pages",
                partition = CachePartitionPolicy.OnDemand(publication = CachePublication.IfAbsent),
            )),
        )
        val pages = cacheKey(
            "nullable-context-pages",
            returns<String>(),
            key = partitioned(
                partition = keyPart<String>("group"),
                key = keyPart<String?>("page", { it ?: "alias" }),
            ),
        )
        cache(pages("a", null)) { _, _ -> "one" }

        val result = cache(pages.many("a", listOf("missing"))) { keys, context ->
            assertEquals(listOf("missing"), keys)
            val aliases = context.entries(listOf(null, "alias", null))
            assertEquals(
                mapOf<String?, String>(null to "one", "alias" to "one"),
                aliases,
            )
            assertEquals(listOf<String?>(null, "alias"), aliases.keys.toList())
            mapOf("missing" to "loaded")
        }

        assertEquals(mapOf<String?, String>("missing" to "loaded"), result)
    }
    test("guarded selected loading preserves an explicit cached null") {
        val fixture = PartitionFixture(nullPlaceholder = "__NULL__")
        val nullable = cacheKey(
            "pages",
            returns<String?>(),
            key = partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")),
        )
        val first = fixture.cache(nullable.many("a", listOf(1, 2))) { _, _ -> mapOf(1 to null) }
        var reloaded = emptyList<Int>()

        val second = fixture.cache(nullable.many("a", listOf(1, 2))) { keys, _ ->
            reloaded = keys
            mapOf(2 to "two")
        }

        assertTrue(first.containsKey(1))
        assertEquals(null, first[1])
        assertEquals(listOf(2), reloaded)
        assertEquals(mapOf(1 to null, 2 to "two"), second)
    }
    test("guarded selected loader reads published siblings through its attempt context") {
        val fixture = PartitionFixture()
        fixture.cache(fixture.pages("a", 9)) { _, _ -> "nine" }

        val result = fixture.cache(fixture.pages.many("a", listOf(1, 2))) { keys, context ->
            assertEquals(listOf(1, 2), keys)
            assertEquals(CacheEntry.Present("nine"), context.entry(9))
            mapOf(1 to "one", 2 to "two")
        }

        assertEquals(mapOf(1 to "one", 2 to "two"), result)
    }
    test("guarded selected loading retries the whole atomic publication after generation replacement") {
        val fixture = PartitionFixture()
        var attempts = 0

        val result = fixture.cache(fixture.pages.many("a", listOf(1, 2))) { keys, _ ->
            attempts++
            if (attempts == 1) fixture.cache.invalidate(fixture.pages.partition("a"))
            keys.associateWith { "attempt-$attempts-$it" }
        }

        assertEquals(2, attempts)
        assertEquals(mapOf(1 to "attempt-2-1", 2 to "attempt-2-2"), result)
    }
    test("guarded selected loading does not combine a hit from an invalidated generation with new values") {
        val fixture = PartitionFixture()
        fixture.cache(fixture.pages("a", 1)) { _, _ -> "old-one" }
        val calls = mutableListOf<List<Int>>()

        val result = fixture.cache(fixture.pages.many("a", listOf(1, 2))) { keys, _ ->
            calls += keys
            if (calls.size == 1) fixture.cache.invalidate(fixture.pages.partition("a"))
            keys.associateWith { "new-$it" }
        }

        assertEquals(listOf(listOf(2), listOf(1, 2)), calls)
        assertEquals(mapOf(1 to "new-1", 2 to "new-2"), result)
    }
    test("a new generation does not join an old-generation scalar flight") {
        val fixture = PartitionFixture(resilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Local))
        coroutineScope {
            val oldStarted = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val old = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache(fixture.pages("a", 1)) { _, _ ->
                    oldStarted.complete(Unit)
                    releaseOld.await()
                    "old-one"
                }
            }
            oldStarted.await()
            try {
                fixture.cache.invalidate(fixture.pages.partition("a"))
                val newStarted = CompletableDeferred<Unit>()
                val fresh = async(start = CoroutineStart.UNDISPATCHED) {
                    fixture.cache(fixture.pages.many("a", listOf(1))) { keys, _ ->
                        newStarted.complete(Unit)
                        keys.associateWith { "new-one" }
                    }
                }

                assertTrue(newStarted.isCompleted, "new generation joined the stale scalar flight")
                assertEquals(mapOf(1 to "new-one"), fresh.await())
            } finally {
                releaseOld.complete(Unit)
                old.await()
            }
        }
    }
    test("guarded sequential selected loading invokes singleton prerequisites in order") {
        val fixture = PartitionFixture(partition = CachePartitionPolicy.SequentialFrom())
        val calls = mutableListOf<List<Int>>()

        val result = fixture.cache(fixture.pages.many("a", listOf(2, 4))) { keys, context ->
            calls += keys
            val key = keys.single()
            val previous = if (key == 0) "" else (context.entry(key - 1) as CacheEntry.Present).value
            mapOf(key to "$previous$key")
        }

        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3), listOf(4)), calls)
        assertEquals(mapOf(2 to "012", 4 to "01234"), result)
    }
    test("guarded sequential selected loading stops dependents at an unresolved prerequisite") {
        val fixture = PartitionFixture(partition = CachePartitionPolicy.SequentialFrom())
        val calls = mutableListOf<List<Int>>()

        val result = fixture.cache(fixture.pages.many("a", listOf(1, 3))) { keys, _ ->
            calls += keys
            val key = keys.single()
            if (key == 2) emptyMap() else mapOf(key to "$key")
        }

        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), calls)
        assertEquals(mapOf(1 to "1"), result)
    }
    test("guarded sequential selected loading restarts all prerequisites after invalidation") {
        val fixture = PartitionFixture(partition = CachePartitionPolicy.SequentialFrom())
        val calls = mutableListOf<Int>()
        var invalidated = false

        val result = fixture.cache(fixture.pages.many("a", listOf(2))) { keys, _ ->
            val key = keys.single()
            calls += key
            if (key == 1 && !invalidated) {
                invalidated = true
                fixture.cache.invalidate(fixture.pages.partition("a"))
            }
            mapOf(key to "$key")
        }

        assertEquals(listOf(0, 1, 0, 1, 2), calls)
        assertEquals(mapOf(2 to "2"), result)
    }

}

private data class MappedPage(val number: Int, val request: String)

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
