package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

val PartitionEnumerationMigrationSpec by testSuite {
    testFixture { EnumerationMigrationFixture() } asContextForEach {
        test("key enumeration rejects entries written without key metadata") {
            cache(forwardPages("owner", 0)) { "existing" }

            val failure = assertFailsWith<IllegalStateException> {
                cache(enumerablePages("owner", 1)) { _, partition ->
                    partition.keys()
                    "next"
                }
            }

            assertTrue(failure.message.orEmpty().contains("Invalidate the partition"), failure.message)
        }

        test("all-entry enumeration rejects entries written without key metadata") {
            cache(forwardPages("owner", 0)) { "existing" }

            val failure = assertFailsWith<IllegalStateException> {
                cache(enumerablePages("owner", 1)) { _, partition ->
                    partition.entries()
                    "next"
                }
            }

            assertTrue(failure.message.orEmpty().contains("Invalidate the partition"), failure.message)
        }
    }
}

private class EnumerationMigrationFixture {
    val cache = Kacheable(InMemoryKacheableStore(), mapOf(
        "pages" to CacheConfig("pages", partition = CachePartitionPolicy.OnDemand()),
    ))
    val forwardPages = cacheKey("pages", returns<String>(), key = partitioned(
        keyPart<String>("owner"), keyPart<Int>("page"),
    ))
    val enumerablePages = cacheKey("pages", returns<String>(), key = partitioned(
        keyPart<String>("owner"), enumerableKeyPart<Int>("page"),
    ))
}
