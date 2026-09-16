package com.github.dave08

import com.github.dave08.kacheable.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

val CachePartitionPolicySpec by testSuite {
    test("ordinary cache configuration needs no partition policy") {
        assertNull(CacheConfig("pages").partition)
    }

    test("sequential partition loading accepts only its starting key and retry bound") {
        val policy = CachePartitionPolicy.SequentialFrom(first = 4, maxLoadAttempts = 2)

        assertEquals(policy, CacheConfig("pages", partition = policy).partition)
    }

    test("independent partition loading permits entry coordination and replacement") {
        val policy = CachePartitionPolicy.OnDemand(
            publication = CachePublication.Replace,
            coordination = CacheCoordination.Entry,
        )

        assertEquals(policy, CacheConfig("pages", partition = policy).partition)
    }

    test("on demand partition policy rejects a retry bound that prevents any attempt") {
        assertFailsWith<IllegalArgumentException> {
            CachePartitionPolicy.OnDemand(maxLoadAttempts = 0)
        }
    }

    test("sequential partition policy rejects a negative retry bound") {
        assertFailsWith<IllegalArgumentException> {
            CachePartitionPolicy.SequentialFrom(maxLoadAttempts = -1)
        }
    }

    test("partition loading rejects snapshot restoration before creating a runtime") {
        assertFailsWith<IllegalArgumentException> {
            CacheConfig(
                "pages",
                partition = CachePartitionPolicy.OnDemand(),
                snapshot = persistentSnapshot(),
            )
        }
    }
}
