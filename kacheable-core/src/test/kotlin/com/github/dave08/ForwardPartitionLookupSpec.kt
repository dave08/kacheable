package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val ForwardPartitionLookupSpec by testSuite {
    test("known sibling reads accept forward-only keys without serializing their input") {
        val pages = cacheKey(
            "pages",
            returns<String>(),
            key = partitioned(keyPart<String>("owner"), keyPart<PageRequest>("page", PageRequest::number)),
        )
        val cache = Kacheable(InMemoryKacheableStore(), mapOf(
            "pages" to CacheConfig("pages", partition = CachePartitionPolicy.OnDemand()),
        ))
        cache(pages("owner", PageRequest(1))) { _, _ -> "first" }

        val result = cache(pages("owner", PageRequest(2))) { _, partition ->
            assertEquals(CacheEntry.Present("first"), partition.entry(PageRequest(1)))
            assertEquals(mapOf(PageRequest(1) to "first"), partition.entries(listOf(PageRequest(1))))
            "second"
        }

        assertEquals("second", result)
    }
}

private data class PageRequest(val number: Int)
