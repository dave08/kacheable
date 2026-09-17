package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val BlockingPartitionContextSpec by testSuite {
    testFixture { BlockingPartitionFixture() } asContextForEach {
        test("blocking sibling reads are lazy until the loader requests them") {
            cache.cache(pages("a", 1)) { _, _ -> "one" }
            val readsBefore = store.fullReads
            cache.cache(pages("a", 2)) { _, _ -> "two" }
            assertEquals(readsBefore, store.fullReads)
            cache.cache(pages("a", 3)) { _, context ->
                assertEquals(mapOf(1 to "one"), context.entries(listOf(1, 9)))
                assertEquals(setOf(1, 2), context.keys())
                assertEquals(readsBefore, store.fullReads)
                "three"
            }
        }
        test("blocking mapped keys support known sibling reads without a codec") {
            data class Request(val page: Int)
            val keys = cacheKey("pages", returns<String>(), key = partitioned(
                partition = keyPart<String>("group"), key = keyPart<Request>("page", Request::page),
            ))
            cache.cache(keys("mapped", Request(1))) { _, _ -> "one" }
            cache.cache(keys("mapped", Request(2))) { _, context ->
                assertEquals(CacheEntry.Present("one"), context.entry(Request(1)))
                "two"
            }
        }
        test("blocking reified enumerable keys retain their declared type") {
            val keys = cacheKey("pages", returns<String>(), key = partitioned(
                partition = keyPart<String>("group"), key = enumerableKeyPart<Long>("page"),
            ))
            cache.cache(keys("longs", 7L)) { _, _ -> "seven" }
            cache.cache(keys("longs", 8L)) { _, context ->
                assertEquals(setOf(7L), context.keys())
                "eight"
            }
        }
        test("blocking saved contexts reject reads after loader return") {
            lateinit var escaped: BlockingCacheLoadContext<Int, String, EnumerableKeyPart<Int>>
            cache.cache(pages("a", 1)) { _, context -> escaped = context; "one" }
            assertFailsWith<IllegalStateException> { escaped.entries() }
        }
        test("blocking zero argument loaders return the guarded published value") {
            assertEquals("one", cache.cache(pages("a", 1), block = { "one" }))
            assertEquals("one", cache.cache(pages("a", 1), block = { error("unexpected loader") }))
        }
        test("blocking published nullable entries differ from missing entries") {
            val cache = BlockingKacheable(store, configs = mapOf("pages" to CacheConfig("pages", partition = CachePartitionPolicy.OnDemand(), nullPlaceholder = "null")))
            val nullable = cacheKey("pages", returns<String?>(), key = partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")))
            cache.cache(nullable("a", 1)) { _, _ -> null }
            cache.cache(nullable("a", 2)) { _, context ->
                assertEquals(CacheEntry.Present(null), context.entry(1))
                assertEquals(CacheEntry.Missing, context.entry(9))
                "two"
            }
        }
    }
    test("blocking guarded and ordinary loaders share concurrency group admission") {
        val group = loadConcurrencyGroup("database", LoadConcurrencyConfig(maxConcurrentLoads = 1, maxQueuedLoads = 0))
        val guarded = cacheKey("guarded", returns<String>(), partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")), loadConcurrency = group)
        val ordinary = cacheKey("ordinary", returns<String>(), exact(keyPart<Int>("id")), loadConcurrency = group)
        val cache = BlockingKacheable(BlockingPartitionTestStore(), configs = mapOf("guarded" to CacheConfig("guarded", partition = CachePartitionPolicy.OnDemand())))
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val first = executor.submit<String> {
                cache.cache(guarded("a", 1)) { _, _ -> entered.countDown(); check(release.await(10, java.util.concurrent.TimeUnit.SECONDS)); "one" }
            }
            check(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertFailsWith<CacheLoadRejectedException> { cache.cache(ordinary(1)) { "two" } }
            release.countDown()
            assertEquals("one", first.get(10, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
    test("blocking default null results remain uncached") {
        val fixture = BlockingPartitionFixture()
        val nullable = cacheKey("pages", returns<String?>(), key = partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")))
        var loads = 0
        repeat(2) { fixture.cache.cache(nullable("a", 1)) { _, _ -> loads++; null } }
        assertEquals(2, loads)
    }
    test("blocking guarded raw invalidation rejects before its mutation callback") {
        val fixture = BlockingPartitionFixture()
        var mutated = false
        assertFailsWith<IllegalArgumentException> {
            fixture.cache.invalidate("pages" to listOf<Any>("a", 1)) { mutated = true }
        }
        assertEquals(false, mutated)
    }
    test("blocking guarded empty caches reject entry invalidation") {
        val fixture = BlockingPartitionFixture()
        assertFailsWith<IllegalArgumentException> { fixture.cache.invalidate(fixture.pages("a", 1)) }
    }
    test("blocking guarded empty caches reject matching invalidation") {
        val fixture = BlockingPartitionFixture()
        val page = matchableKeyPart<Int>("page")
        val pages = cacheKey("pages", returns<String>(), key = partitioned(partition = keyPart<String>("group"), key = page))
        assertFailsWith<IllegalArgumentException> { fixture.cache.invalidate(pages.matching("a", page(1))) }
    }
    test("blocking guarded raw calls reject publication bypass") {
        val fixture = BlockingPartitionFixture()
        assertFailsWith<IllegalArgumentException> { fixture.cache.cache<String>("pages") { "one" } }
    }
    test("blocking sequential loading publishes predecessors before successor loaders") {
        val fixture = BlockingPartitionFixture(CacheConfig("pages", partition = CachePartitionPolicy.SequentialFrom()))
        val loaded = mutableListOf<Int>()
        val value = fixture.cache.cache(fixture.pages("a", 3)) { key, context ->
            loaded += key
            if (key == 0) "0" else (context.entry(key - 1) as CacheEntry.Present).value + key
        }
        assertEquals("0123", value)
        assertEquals(listOf(0, 1, 2, 3), loaded)
    }
    test("blocking contextual loaders require generation checked configuration") {
        val fixture = BlockingPartitionFixture(CacheConfig("pages"))
        assertFailsWith<IllegalArgumentException> { fixture.cache.cache(fixture.pages("a", 1)) { _, _ -> "one" } }
    }
    test("blocking ordinary zero argument loaders preserve cache hits") {
        val fixture = BlockingPartitionFixture(CacheConfig("pages"))
        assertEquals("one", fixture.cache.cache(fixture.pages("a", 1), block = { "one" }))
        assertEquals("one", fixture.cache.cache(fixture.pages("a", 1), block = { error("unexpected loader") }))
    }
    test("blocking guarded zero argument loader preserves a cached null") {
        val fixture = BlockingPartitionFixture(CacheConfig("pages", partition = CachePartitionPolicy.OnDemand(), nullPlaceholder = "__NULL__"))
        val nullable = cacheKey(
            "pages",
            returns<String?>(),
            key = partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")),
        )
        assertEquals(null, fixture.cache.cache(nullable("a", 1), block = { null }))
        assertEquals(null, fixture.cache.cache(nullable("a", 1), block = { error("Cached null must not invoke the loader") }))
    }

}

private class BlockingPartitionFixture(config: CacheConfig = CacheConfig("pages", partition = CachePartitionPolicy.OnDemand(publication = CachePublication.IfAbsent))) {
    val store = BlockingPartitionTestStore()
    val cache = BlockingKacheable(store, configs = mapOf("pages" to config))
    val pages = cacheKey("pages", returns<String>(), key = partitioned(partition = keyPart<String>("group"), key = enumerableKeyPart<Int>("page")))
}
