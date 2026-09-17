package com.github.dave08

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val CacheManyCoordinationSpec by testSuite {
    test("overlapping local single-flight batches load each missing entry once") {
        val fixture = ManyCoordinationFixture()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var firstKeys = emptyList<Int>()
        var secondKeys = emptyList<Int>()

        val results = coroutineScope {
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache(fixture.values.many(1, 2)) { keys, _ ->
                    firstKeys = keys
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    keys.associateWith { "value-$it" }
                }
            }
            firstStarted.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache(fixture.values.many(2, 3)) { keys, _ ->
                    secondKeys = keys
                    keys.associateWith { "value-$it" }
                }
            }
            releaseFirst.complete(Unit)
            first.await() to second.await()
        }

        assertEquals(listOf(1, 2), firstKeys)
        assertEquals(listOf(3), secondKeys)
        assertEquals(mapOf(1 to "value-1", 2 to "value-2"), results.first)
        assertEquals(mapOf(2 to "value-2", 3 to "value-3"), results.second)
    }

    test("scalar callers join a batch-owned cached null") {
        val fixture = ManyCoordinationFixture(nullable = true)
        val batchStarted = CompletableDeferred<Unit>()
        val releaseBatch = CompletableDeferred<Unit>()
        var scalarLoads = 0

        val scalarResult = coroutineScope {
            val batch = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache(fixture.nullableValues.many(1)) { _, _ ->
                    batchStarted.complete(Unit)
                    releaseBatch.await()
                    mapOf(1 to null)
                }
            }
            batchStarted.await()
            val scalar = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache(fixture.nullableValues(1)) {
                    scalarLoads++
                    "unexpected"
                }
            }
            releaseBatch.complete(Unit)
            scalar.await().also { batch.await() }
        }

        assertEquals(null, scalarResult)
        assertEquals(0, scalarLoads)
    }

    test("batch callers join a scalar-owned cached null and load only their other misses") {
        val fixture = ManyCoordinationFixture(nullable = true)
        val scalarStarted = CompletableDeferred<Unit>()
        val releaseScalar = CompletableDeferred<Unit>()
        var batchKeys = emptyList<Int>()

        val result = coroutineScope {
            val scalar = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache(fixture.nullableValues(1)) {
                    scalarStarted.complete(Unit)
                    releaseScalar.await()
                    null
                }
            }
            scalarStarted.await()
            val batch = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache(fixture.nullableValues.many(1, 2)) { keys, _ ->
                    batchKeys = keys
                    mapOf(2 to "two")
                }
            }
            releaseScalar.complete(Unit)
            batch.await().also { scalar.await() }
        }

        assertEquals(listOf(2), batchKeys)
        assertTrue(result.containsKey(1))
        assertEquals(null, result[1])
        assertEquals("two", result[2])
    }

    test("cancelling a batch owner releases every entry flight for the next caller") {
        val fixture = ManyCoordinationFixture()
        val firstStarted = CompletableDeferred<Unit>()

        coroutineScope {
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache(fixture.values.many(1, 2)) { _, _ ->
                    firstStarted.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    emptyMap()
                }
            }
            firstStarted.await()
            cancelled.cancelAndJoin()
        }

        var retryKeys = emptyList<Int>()
        val result = fixture.cache(fixture.values.many(1, 2)) { keys, _ ->
            retryKeys = keys
            keys.associateWith { "retry-$it" }
        }

        assertEquals(listOf(1, 2), retryKeys)
        assertEquals(mapOf(1 to "retry-1", 2 to "retry-2"), result)
    }

    test("an omitted shared entry does not fail joiners that resolved other entries") {
        val fixture = ManyCoordinationFixture()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val secondResult = coroutineScope {
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache(fixture.values.many(1, 2)) { _, _ ->
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    mapOf(1 to "one")
                }
            }
            firstStarted.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.cache(fixture.values.many(1, 2, 3)) { keys, _ ->
                    assertEquals(listOf(3), keys)
                    mapOf(3 to "three")
                }
            }
            releaseFirst.complete(Unit)
            second.await().also { first.await() }
        }

        assertEquals(mapOf(1 to "one", 3 to "three"), secondResult)
    }
}

private class ManyCoordinationFixture(nullable: Boolean = false) {
    private val name = if (nullable) "many-coordination-nullable" else "many-coordination"
    private val store = InMemoryKacheableStore()
    val cache = Kacheable(
        store,
        configs = if (nullable) mapOf(name to CacheConfig(name, nullPlaceholder = "<null>")) else emptyMap(),
        defaultResilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Local),
    )
    val values = cacheKey(name, returns<String>(), exact(keyPart<Int>("id")))
    val nullableValues = cacheKey(name, returns<String?>(), exact(keyPart<Int>("id")))
}
