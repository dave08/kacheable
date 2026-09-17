package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.AdmissionAwareDistributedSingleFlightStore
import com.github.dave08.kacheable.store.DistributedLoadLease
import com.github.dave08.kacheable.store.cacheCodec
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

val CacheManyFailureIsolationSpec by testSuite {
    testFixture { ManyFailureFixture() } asContextForEach {
        for (requested in listOf(listOf(1, 2, 3), listOf(2, 1, 3))) {
            test("a failed shared entry preserves successful siblings in request order $requested") {
                val failed = IllegalStateException("entry two failed")

                val result = coroutineScope {
                    val successfulOwner = async(start = CoroutineStart.UNDISPATCHED) {
                        cache(values(1), cacheIf = { false }) { release.await(); null }
                    }
                    val failingOwner = async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching { cache(values(2)) { release.await(); throw failed } }
                    }
                    val batch = async(start = CoroutineStart.UNDISPATCHED) {
                        cache(values.many(requested), missPolicy = CacheMissPolicy.load { error ->
                            fallbacks += error
                            "fallback"
                        }) { keys, _ ->
                            assertEquals(listOf(3), keys)
                            mapOf(3 to "three")
                        }
                    }
                    release.complete(Unit)
                    batch.await().also { successfulOwner.await(); failingOwner.await() }
                }

                assertEquals(mapOf(1 to null, 2 to "fallback", 3 to "three"), result)
                assertEquals(1, fallbacks.size)
                assertOriginalCause(failed, fallbacks.single())
            }
        }

        test("a failed batch-owned entry still awaits a successful scalar-owned entry") {
            val failed = IllegalStateException("owned entry failed")
            val result = coroutineScope {
                val scalar = async(start = CoroutineStart.UNDISPATCHED) {
                    cache(values(1), cacheIf = { false }) { release.await(); "one" }
                }
                val batch = async(start = CoroutineStart.UNDISPATCHED) {
                    cache(values.many(1, 2), missPolicy = CacheMissPolicy.load { error ->
                        fallbacks += error
                        "fallback"
                    }) { keys, _ ->
                        assertEquals(listOf(2), keys)
                        throw failed
                    }
                }
                release.complete(Unit)
                batch.await().also { scalar.await() }
            }
            assertEquals(mapOf(1 to "one", 2 to "fallback"), result)
            assertEquals(1, fallbacks.size)
            assertOriginalCause(failed, fallbacks.single())
        }

        test("each failed shared entry supplies its own cause to its fallback") {
            val failures = mapOf(1 to IllegalStateException("one failed"), 2 to IllegalArgumentException("two failed"))
            val result = coroutineScope {
                val owners = failures.map { (key, failure) ->
                    async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching { cache(values(key)) { release.await(); throw failure } }
                    }
                }
                val batch = async(start = CoroutineStart.UNDISPATCHED) {
                    cache(values.many(1, 2), missPolicy = CacheMissPolicy.load { error ->
                        fallbacks += error
                        error.message
                    }) { _, _ -> error("the batch must join both owners") }
                }
                release.complete(Unit)
                batch.await().also { owners.forEach { it.await() } }
            }
            assertEquals(mapOf(1 to "one failed", 2 to "two failed"), result)
            assertEquals(2, fallbacks.size)
            failures.values.zip(fallbacks).forEach { (original, fallback) -> assertOriginalCause(original, fallback) }
        }

        test("an inner batch-loader timeout still awaits a successful scalar-owned entry") {
            val result = coroutineScope {
                val scalar = async(start = CoroutineStart.UNDISPATCHED) {
                    cache(values(1), cacheIf = { false }) { release.await(); "one" }
                }
                val batch = async(start = CoroutineStart.UNDISPATCHED) {
                    cache(values.many(1, 2), missPolicy = CacheMissPolicy.load { error ->
                        fallbacks += error
                        "timeout fallback"
                    }) { keys, _ ->
                        assertEquals(listOf(2), keys)
                        withTimeout(0) { emptyMap() }
                    }
                }
                release.complete(Unit)
                batch.await().also { scalar.await() }
            }
            assertEquals(mapOf(1 to "one", 2 to "timeout fallback"), result)
            assertEquals(1, fallbacks.size)
            assertIs<TimeoutCancellationException>(fallbacks.single())
        }

        test("a timed out shared owner does not discard another owner's successful value") {
            val result = coroutineScope {
                val successfulOwner = async(start = CoroutineStart.UNDISPATCHED) {
                    cache(values(1), cacheIf = { false }) { release.await(); "one" }
                }
                val timedOutOwner = async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { cache(values(2)) { release.await(); withTimeout(0) { "unreachable" } } }
                }
                val batch = async(start = CoroutineStart.UNDISPATCHED) {
                    cache(values.many(2, 1, 3), missPolicy = CacheMissPolicy.load { error ->
                        fallbacks += error
                        "timeout fallback"
                    }) { keys, _ ->
                        assertEquals(listOf(3), keys)
                        mapOf(3 to "three")
                    }
                }
                release.complete(Unit)
                batch.await().also { successfulOwner.await(); timedOutOwner.await() }
            }
            assertEquals(mapOf(1 to "one", 2 to "timeout fallback", 3 to "three"), result)
            assertEquals(1, fallbacks.size)
            assertIs<TimeoutCancellationException>(fallbacks.single())
        }
    }

    testFixture { CoordinationReadFixture() } asContextForEach {
        test("a distributed coordination read remains resolved when another entry load fails") {
            val result = cache(values.many(1, 2), missPolicy = CacheMissPolicy.load { error ->
                fallbacks += error
                "fallback"
            }) { keys, _ ->
                assertEquals(listOf(2), keys)
                throw failed
            }

            assertEquals(mapOf(1 to "one", 2 to "fallback"), result)
            assertEquals(1, fallbacks.size)
            assertOriginalCause(failed, fallbacks.single())
        }
    }
}

private class ManyFailureFixture {
    val cache = Kacheable(InMemoryKacheableStore(), defaultResilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Local))
    val values = cacheKey("many-failure-isolation", returns<String?>(), exact(keyPart<Int>("id")))
    val release = CompletableDeferred<Unit>()
    val fallbacks = mutableListOf<Throwable>()
}

private fun assertOriginalCause(original: Throwable, actual: Throwable) {
    // Coroutine stacktrace recovery may copy the exception while retaining its original cause.
    assertTrue(generateSequence(actual) { it.cause }.any { it === original })
}

private class CoordinationReadFixture {
    private val name = "many-coordination-read-failure"
    val failed = IllegalStateException("second entry failed")
    val fallbacks = mutableListOf<Throwable>()
    val values = cacheKey(name, returns<String>(), exact(keyPart<Int>("id")))
    val cache = Kacheable(
        ConcurrentPublicationStore("$name:1", cacheCodec(serializer<String>()).encode("one")),
        defaultResilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Redis),
    )
}

/** Publishes a sibling between the hot read and the distributed coordination read. */
private class ConcurrentPublicationStore(
    private val publishedKey: String,
    private val publishedValue: String,
    private val delegate: InMemoryKacheableStore = InMemoryKacheableStore(),
) : KacheableStore by delegate, AdmissionAwareDistributedSingleFlightStore {
    private var reads = 0

    override suspend fun getValues(keys: List<String>): Map<String, String> {
        if (++reads == 2) delegate.set(publishedKey, publishedValue)
        return delegate.getValues(keys)
    }

    override suspend fun tryAcquireDistributedLoadLease(key: String, lockLease: Duration): DistributedLoadLease =
        object : DistributedLoadLease { override suspend fun release() = Unit }

    override suspend fun <R> runWithDistributedSingleFlight(
        key: String,
        lockLease: Duration,
        waitTimeout: Duration,
        pollInterval: Duration,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
    ): R = error("selected coordination must use individual leases")
}
