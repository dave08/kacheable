package com.github.dave08

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.TestCompartment
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.invocation
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private fun TestConfig.kacheablePressure(): TestConfig = this
    .invocation(TestConfig.Invocation.Concurrent)
    .testScope(isEnabled = false)
    .aroundEachTest { action ->
        withTimeout(5.seconds) { action() }
    }

val CacheResilienceSpec by testSuite(
    compartment = { TestCompartment.Concurrent },
    testConfig = TestConfig.kacheablePressure(),
) {
    test("single-flight none allows duplicate same-key loaders") {
        val calls = AtomicInteger(0)
        val cache = cacheWith(CacheResilienceConfig(singleFlight = SingleFlightMode.None))

        val results = callSameKeyConcurrently {
            cache.cache("foo", "same") {
                calls.incrementAndGet()
                delay(50.milliseconds)
                "value"
            }
        }

        expectThat(results.distinct()).containsExactly("value")
        expectThat(calls.get()).isEqualTo(results.size)
    }

    test("local single-flight runs one loader for the same key") {
        val calls = AtomicInteger(0)
        val cache = cacheWith(CacheResilienceConfig(singleFlight = SingleFlightMode.Local))

        val results = callSameKeyConcurrently {
            cache.cache("foo", "same") {
                calls.incrementAndGet()
                delay(50.milliseconds)
                "value"
            }
        }

        expectThat(results.distinct()).containsExactly("value")
        expectThat(calls.get()).isEqualTo(1)
    }

    test("maxConcurrentLoads limits different cold keys") {
        val activeLoads = AtomicInteger(0)
        val maxActiveLoads = AtomicInteger(0)
        val cache = cacheWith(
            CacheResilienceConfig(
                singleFlight = SingleFlightMode.None,
                maxConcurrentLoads = 2,
            ),
        )

        coroutineScope {
            val start = CompletableDeferred<Unit>()
            val jobs = (1..8).map { key ->
                async {
                    start.await()
                    cache.cache("foo", key) {
                        val active = activeLoads.incrementAndGet()
                        maxActiveLoads.updateAndGet { current -> maxOf(current, active) }
                        delay(40.milliseconds)
                        activeLoads.decrementAndGet()
                        "value-$key"
                    }
                }
            }

            start.complete(Unit)
            jobs.awaitAll()
        }

        expectThat(maxActiveLoads.get()).isEqualTo(2)
    }

    test("local single-flight clears failed loads") {
        val calls = AtomicInteger(0)
        val cache = cacheWith(CacheResilienceConfig(singleFlight = SingleFlightMode.Local))

        assertFailsWith<IllegalStateException> {
            cache.cache<String>("foo", "same") {
                calls.incrementAndGet()
                error("boom")
            }
        }

        val result = cache.cache("foo", "same") {
            calls.incrementAndGet()
            "value"
        }

        expectThat(result).isEqualTo("value")
        expectThat(calls.get()).isEqualTo(2)
    }

    test("timeout clears local single-flight loads") {
        val calls = AtomicInteger(0)
        val cache = cacheWith(
            CacheResilienceConfig(
                singleFlight = SingleFlightMode.Local,
                loadTimeout = 30.milliseconds,
            ),
        )

        assertFailsWith<TimeoutCancellationException> {
            cache.cache<String>("foo", "same") {
                calls.incrementAndGet()
                delay(250.milliseconds)
                "too-late"
            }
        }

        val result = cache.cache("foo", "same") {
            calls.incrementAndGet()
            "value"
        }

        expectThat(result).isEqualTo("value")
        expectThat(calls.get()).isEqualTo(2)
    }

    test("Redis single-flight fails fast when the store does not support distributed coordination") {
        assertFailsWith<IllegalArgumentException> {
            Kacheable(
                store = InMemoryKacheableStore(),
                defaultResilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Redis),
            )
        }
    }
}

private fun cacheWith(resilience: CacheResilienceConfig): Kacheable =
    Kacheable(
        store = InMemoryKacheableStore(),
        configs = mapOf("foo" to CacheConfig("foo", resilience = resilience)),
    )

private suspend fun callSameKeyConcurrently(block: suspend () -> String): List<String> =
    coroutineScope {
        val start = CompletableDeferred<Unit>()
        val jobs = (1..20).map {
            async {
                start.await()
                block()
            }
        }
        start.complete(Unit)
        jobs.awaitAll()
    }
