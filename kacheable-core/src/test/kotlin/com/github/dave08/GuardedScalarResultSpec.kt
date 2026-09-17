package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

val GuardedScalarResultSpec by testSuite {
    testFixture { GuardedScalarResultFixture() } asContextForEach {
        test("a guarded scalar retains its uncached result when another loader publishes the field") {
            val result = cache(pages(1), cacheIf = { false }) { _, _ ->
                cache(pages(1)) { _, _ -> "stored" }
                "uncached"
            }

            assertEquals("uncached", result)
            assertEquals("stored", cache(pages(1)) { _, _ -> error("unexpected loader") })
        }
        test("a guarded scalar rejects a cached key before its sequential origin") {
            cache(pages(1)) { _, _ -> "stored" }
            val sequential = Kacheable(store, configs = mapOf("pages" to CacheConfig(
                "pages", partition = CachePartitionPolicy.SequentialFrom(first = 2),
            )))

            assertFailsWith<IllegalArgumentException> {
                sequential(pages(1)) { _, _ -> error("unexpected loader") }
            }
        }
        test("a guarded scalar retains an uncacheable null when another loader publishes the field") {
            val result = cache(pages(1)) { _, _ ->
                cache(pages(1)) { _, _ -> "stored" }
                null
            }

            assertNull(result)
            assertEquals("stored", cache(pages(1)) { _, _ -> error("unexpected loader") })
        }
    }
}

private class GuardedScalarResultFixture {
    val store = InMemoryKacheableStore()
    val cache = Kacheable(store, configs = mapOf("pages" to CacheConfig(
        "pages", partition = CachePartitionPolicy.OnDemand(publication = CachePublication.IfAbsent),
    )))
    val pages = cacheKey("pages", returns<String?>(), key = partitioned(key = keyPart<Int>("page")))
}
