package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.cache
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import com.github.dave08.kacheable.store.KacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

val PartitionStartupValidationSpec by testSuite {
    test("partition configuration rejects access expiry during construction") {
        assertFailsWith<IllegalArgumentException> {
            CacheConfig("pages", partition = CachePartitionPolicy.OnDemand(), expiryType = ExpiryType.after_access)
        }
    }
    listOf("zero" to Duration.ZERO, "negative" to (-1).milliseconds, "submillisecond" to 500.microseconds).forEach { (name, expiry) ->
        test("partition configuration rejects $name write expiry during construction") {
            assertFailsWith<IllegalArgumentException> {
                CacheConfig("pages", partition = CachePartitionPolicy.OnDemand(), expiryType = ExpiryType.after_write, expiry = expiry)
            }
        }
    }
    listOf(
        "failure" to CacheResilienceConfig(staleOnFailure = true),
        "timeout" to CacheResilienceConfig(staleOnTimeout = true),
    ).forEach { (name, resilience) ->
        test("partition configuration rejects stale $name fallback during construction") {
            assertFailsWith<IllegalArgumentException> {
                CacheConfig("pages", partition = CachePartitionPolicy.OnDemand(), resilience = resilience)
            }
        }
        test("suspending runtime rejects inherited stale $name fallback during construction") {
            assertFailsWith<IllegalArgumentException> {
                Kacheable(InMemoryKacheableStore(), configs = guardedConfigs(), defaultResilience = resilience)
            }
        }
    }
    test("suspending runtime rejects a store without atomic partition support during construction") {
        val ordinaryStore = object : KacheableStore by InMemoryKacheableStore() {}
        assertFailsWith<IllegalArgumentException> {
            Kacheable(ordinaryStore, configs = guardedConfigs())
        }
    }
    test("blocking runtime rejects a store without atomic partition support during construction") {
        val ordinaryStore = object : BlockingKacheableStore by BlockingPartitionTestStore() {}
        assertFailsWith<IllegalArgumentException> {
            BlockingKacheable(ordinaryStore, configs = guardedConfigs())
        }
    }
    test("blocking runtime rejects coroutine resilience during construction") {
        assertFailsWith<IllegalArgumentException> {
            BlockingKacheable(BlockingPartitionTestStore(), configs = guardedConfigs(CacheResilienceConfig(singleFlight = SingleFlightMode.Local)))
        }
    }
    test("explicit partition resilience overrides stale defaults during construction") {
        Kacheable(InMemoryKacheableStore(), configs = guardedConfigs(CacheResilienceConfig()), defaultResilience = CacheResilienceConfig(staleOnFailure = true))
    }
    test("ordinary runtime construction does not require atomic partition support") {
        val ordinaryStore = object : KacheableStore by InMemoryKacheableStore() {}
        Kacheable(ordinaryStore, configs = mapOf("pages" to CacheConfig("pages", expiryType = ExpiryType.after_access)))
    }
    test("suspending runtime retains its validated configuration when the caller changes the map") {
        val configs = mutableMapOf("pages" to CacheConfig("pages"))
        val cache = Kacheable(InMemoryKacheableStore(), configs = configs)
        val pages = cacheKey("pages", returns<String>(), key = exact(keyPart<Int>("page")))
        configs["pages"] = CacheConfig("pages", partition = CachePartitionPolicy.OnDemand())

        assertEquals("one", cache.cache(pages(1)) { "one" })
    }
    test("blocking runtime retains its validated configuration when the caller changes the map") {
        val configs = mutableMapOf("pages" to CacheConfig("pages"))
        val cache = BlockingKacheable(BlockingPartitionTestStore(), configs = configs)
        val pages = cacheKey("pages", returns<String>(), key = exact(keyPart<Int>("page")))
        configs["pages"] = CacheConfig("pages", partition = CachePartitionPolicy.OnDemand())

        assertEquals("one", cache.cache(pages(1)) { "one" })
    }
    listOf("infinite" to Duration.INFINITE, "millisecond" to 1.milliseconds).forEach { (name, expiry) ->
        test("partition configuration accepts $name write expiry during construction") {
            CacheConfig("pages", partition = CachePartitionPolicy.OnDemand(), expiryType = ExpiryType.after_write, expiry = expiry)
        }
    }
}

private fun guardedConfigs(resilience: CacheResilienceConfig? = null) = mapOf(
    "pages" to CacheConfig("pages", partition = CachePartitionPolicy.OnDemand(), resilience = resilience),
)
