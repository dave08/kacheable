package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val PartitionReadScopeSpec by testSuite {
    testFixture { PartitionReadFixture() } asContextForEach {
        test("listing partition keys does not fetch sibling payloads") {
            cache(pages("catalog", 0)) { "large-payload".repeat(10_000) }

            cache(pages("catalog", 1)) { _, partition ->
                recordPayloadReads()
                assertEquals(setOf(0), partition.keys())
                assertEquals(emptyList(), payloadReads)
                "next"
            }
        }

        test("reading selected siblings does not fetch the remaining partition") {
            cache(pages("catalog", 0)) { "needed" }
            cache(pages("catalog", 1)) { "unrelated".repeat(10_000) }

            cache(pages("catalog", 2)) { _, partition ->
                recordPayloadReads()
                assertEquals(mapOf(0 to "needed"), partition.entries(listOf(0)))
                assertEquals(listOf<List<String>?>(listOf("0")), payloadReads)
                "next"
            }
        }
    }
}

private class PartitionReadFixture {
    private val backing = InMemoryKacheableStore()
    val payloadReads = mutableListOf<List<String>?>()
    private var recording = false
    private val store = object : KacheableStore by backing, VersionedHashOperations by backing {
        override suspend fun readHash(key: String, version: HashVersion, fields: List<String>?): Map<String, String>? {
            if (recording) payloadReads += fields
            return backing.readHash(key, version, fields)
        }
    }
    val pages = cacheKey("pages", returns<String>(), key = partitioned(keyPart<String>("owner"), enumerableKeyPart<Int>("page")))
    val cache = Kacheable(store, mapOf("pages" to CacheConfig("pages", partition = CachePartitionPolicy.OnDemand())))

    fun recordPayloadReads() {
        payloadReads.clear()
        recording = true
    }
}
