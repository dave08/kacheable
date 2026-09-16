package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.cache
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

val PartitionTelemetrySpec by testSuite {
    testFixture { PartitionTelemetryFixture() } asContextForEach {
        test("partition coordinator rechecks have their own read category") {
            cache.cache(pages("a", 1)) { _, _ -> "one" }
            assertTrue(reads().any { it.attempt == CacheReadAttempt.SingleFlightRecheck })
        }

        test("partition sibling reads report storage work") {
            cache.cache(pages("a", 1)) { _, context ->
                context.entry(0)
                "one"
            }
            assertTrue(reads().any { it.attempt == CacheReadAttempt.PartitionContext })
        }

        test("partition metadata enumeration reports storage work") {
            cache.cache(pages("a", 1)) { _, context ->
                context.keys()
                "one"
            }
            assertTrue(reads().any { it.attempt == CacheReadAttempt.PartitionMetadata })
        }

        test("partition publication winner reports a skipped write") {
            store.winner = "winner"
            assertEquals("winner", cache.cache(pages("a", 1)) { _, _ -> "loser" })
            assertEquals(listOf(CacheWriteResult.Skipped), writes().map { it.result })
        }

        test("partition publication failures report failed writes") {
            store.failPublication = true
            assertFailsWith<IllegalStateException> { cache.cache(pages("a", 1)) { _, _ -> "one" } }
            assertEquals(listOf(CacheWriteResult.Failed), writes().map { it.result })
        }

        test("partition retry reports a conflict event") {
            store.conflictOnce = true
            assertEquals("one", cache.cache(pages("a", 1)) { _, _ -> "one" })
            val conflicts = telemetry.recentEvents().map { it.stage }
                .filterIsInstance<CacheDiagnosticStage.PartitionConflict>()
            assertEquals(listOf(CacheDiagnosticStage.PartitionConflict(attempt = 1, willRetry = true)), conflicts)
        }

        test("partition coordination reports waiting for a different entry") {
            coroutineScope {
                val release = CompletableDeferred<Unit>()
                val leader = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.cache(pages("a", 1)) { _, _ ->
                        release.await()
                        "one"
                    }
                }
                try {
                    val follower = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(pages("a", 2)) { _, _ -> "two" }
                    }
                    release.complete(Unit)
                    leader.await()
                    follower.await()
                } finally {
                    release.complete(Unit)
                }
            }
            val waits = telemetry.recentEvents().map { it.stage }.filterIsInstance<CacheDiagnosticStage.LoadWait>()
            assertTrue(waits.any { it.reason == CacheWaitReason.PartitionCoordination })
        }
    }

    test("guarded blocking loaders retain the parent of nested ordinary cache calls") {
        val telemetry = InMemoryCacheTelemetry(recentEventCapacity = 100)
        val cache = BlockingKacheable(
            BlockingPartitionTestStore(), telemetry = telemetry,
            configs = mapOf("pages" to CacheConfig("pages", partition = CachePartitionPolicy.OnDemand())),
        )
        val pages = cacheKey("pages", returns<String>(), key = partitioned(key = keyPart<Int>("page")))
        val inner = cacheKey("inner", returns<String>(), key = exact(keyPart<Int>("id")))
        cache.cache(pages(1)) { _, _ ->
            cache.cache(inner(1)) { "nested" }
        }
        val starts = telemetry.recentEvents().filter { it.stage == CacheDiagnosticStage.Started }
        val parent = starts.single { it.context.cacheName == "pages" }
        val child = starts.single { it.context.cacheName == "inner" }
        assertEquals(parent.context.operationId, child.context.parentOperationId)
    }
}

private class PartitionTelemetryFixture {
    val telemetry = InMemoryCacheTelemetry(recentEventCapacity = 200)
    val store = PartitionTelemetryStore()
    val cache = Kacheable(store, telemetry = telemetry, configs = mapOf(
        "pages" to CacheConfig("pages", partition = CachePartitionPolicy.OnDemand(
            publication = CachePublication.IfAbsent, coordination = CacheCoordination.Partition,
        )),
    ))
    val pages = cacheKey("pages", returns<String>(), key = partitioned(
        partition = keyPart<String>("group"), key = enumerableKeyPart<Int>("page"),
    ))
    fun reads() = telemetry.recentEvents().map { it.stage }.filterIsInstance<CacheDiagnosticStage.StorageRead>()
    fun writes() = telemetry.recentEvents().map { it.stage }.filterIsInstance<CacheDiagnosticStage.StorageWrite>()
}
