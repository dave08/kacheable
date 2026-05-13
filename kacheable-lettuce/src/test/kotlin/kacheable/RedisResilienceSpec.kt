package kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.redis.RedisKacheableStore
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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.filterIsInstance
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private fun TestConfig.redisPressure(): TestConfig = this
    .invocation(TestConfig.Invocation.Concurrent)
    .testScope(isEnabled = false)
    .aroundEachTest { action ->
        withTimeout(20.seconds) { action() }
    }

val RedisResilienceSpec by testSuite(
    compartment = { TestCompartment.Concurrent },
    testConfig = TestConfig.redisPressure(),
) {
    test("Redis single-flight runs one loader for many same-key callers") {
        RedisFixture.start("redis:7-alpine").use { fixture ->
            val calls = AtomicInteger(0)
            val cache = Kacheable(
                store = RedisKacheableStore(fixture.connection),
                configs = redisSingleFlightConfig(),
            )

            val results = callSameKeyConcurrently {
                cache.cache("foo", "same") {
                    calls.incrementAndGet()
                    delay(100.milliseconds)
                    "value"
                }
            }

            expectThat(results.distinct()).containsExactly("value")
            expectThat(calls.get()).isEqualTo(1)
        }
    }

    test("Redis single-flight coordinates two cache instances") {
        RedisFixture.start("redis:7-alpine").use { fixture ->
            fixture.newConnection().use { secondConnection ->
                val calls = AtomicInteger(0)
                val first = Kacheable(
                    store = RedisKacheableStore(fixture.connection),
                    configs = redisSingleFlightConfig(),
                )
                val second = Kacheable(
                    store = RedisKacheableStore(secondConnection),
                    configs = redisSingleFlightConfig(),
                )

                val results = coroutineScope {
                    val start = CompletableDeferred<Unit>()
                    listOf(
                        async {
                            start.await()
                            first.cache("foo", "same") {
                                calls.incrementAndGet()
                                delay(100.milliseconds)
                                "value"
                            }
                        },
                        async {
                            start.await()
                            second.cache("foo", "same") {
                                calls.incrementAndGet()
                                delay(100.milliseconds)
                                "value"
                            }
                        },
                    ).also { start.complete(Unit) }.awaitAll()
                }

                expectThat(results.distinct()).containsExactly("value")
                expectThat(calls.get()).isEqualTo(1)
            }
        }
    }

    test("Redis single-flight releases the lock when the winner fails") {
        RedisFixture.start("redis:7-alpine").use { fixture ->
            fixture.newConnection().use { secondConnection ->
                val calls = AtomicInteger(0)
                val first = Kacheable(
                    store = RedisKacheableStore(fixture.connection),
                    configs = redisSingleFlightConfig(),
                )
                val second = Kacheable(
                    store = RedisKacheableStore(secondConnection),
                    configs = redisSingleFlightConfig(),
                )

                val outcomes = supervisorScope {
                    val start = CompletableDeferred<Unit>()
                    listOf(
                        async {
                            start.await()
                            runCatching {
                                first.cache("foo", "same") {
                                    if (calls.incrementAndGet() == 1) error("boom")
                                    "value"
                                }
                            }
                        },
                        async {
                            start.await()
                            runCatching {
                                second.cache("foo", "same") {
                                    if (calls.incrementAndGet() == 1) error("boom")
                                    "value"
                                }
                            }
                        },
                    ).also { start.complete(Unit) }.awaitAll()
                }

                expectThat(outcomes.filter { it.isSuccess }.map { it.getOrThrow() }).containsExactly("value")
                expectThat(outcomes.mapNotNull { it.exceptionOrNull() }).filterIsInstance<IllegalStateException>().hasSize(1)
                expectThat(calls.get()).isEqualTo(2)
            }
        }
    }

    test("Redis mutate records before opening MULTI and leaves connection usable after block failure") {
        RedisFixture.start("redis:7-alpine").use { fixture ->
            val store = RedisKacheableStore(fixture.connection)

            assertFailsWith<IllegalStateException> {
                store.mutate {
                    set("songs:1", """{"id":1}""")
                    error("boom before redis transaction")
                }
            }

            expectThat(fixture.commands.get("songs:1")).isEqualTo(null)

            store.mutate {
                set("songs:2", """{"id":2}""")
            }

            expectThat(fixture.commands.get("songs:2")).isEqualTo("""{"id":2}""")
        }
    }
}

private fun redisSingleFlightConfig(): Map<String, CacheConfig> =
    mapOf(
        "foo" to CacheConfig(
            name = "foo",
            resilience = CacheResilienceConfig(
                singleFlight = SingleFlightMode.Redis,
                loadTimeout = 2.seconds,
            ),
        ),
    )

private suspend fun callSameKeyConcurrently(block: suspend () -> String): List<String> =
    coroutineScope {
        val start = CompletableDeferred<Unit>()
        val jobs = (1..30).map {
            async {
                start.await()
                block()
            }
        }
        start.complete(Unit)
        jobs.awaitAll()
    }
