package com.github.dave08

import com.github.dave08.kacheable.CacheLoadRole
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheObservation
import com.github.dave08.kacheable.CacheOperation
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.CacheTelemetry
import com.github.dave08.kacheable.CacheWaitReason
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.LoadConcurrencyConfig
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.loadConcurrencyGroup
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val manyAdmissionId = keyPart<Int>("id")

val CacheManyAdmissionSpec by testSuite {
    test("a foreground scalar can claim an entry while a background batch waits for admission") {
        verifyForegroundClaimsBeforeQueuedBatch(ForegroundCaller.Scalar)
    }

    test("a foreground batch can claim an entry while a background batch waits for admission") {
        verifyForegroundClaimsBeforeQueuedBatch(ForegroundCaller.Batch)
    }
}

private suspend fun verifyForegroundClaimsBeforeQueuedBatch(caller: ForegroundCaller) {
    withTimeout(5.seconds) {
        coroutineScope {
            val suffix = caller.name.lowercase()
            val group = loadConcurrencyGroup(
                "many-admission-$suffix",
                LoadConcurrencyConfig(
                    maxConcurrentLoads = 2,
                    maxConcurrentBackgroundLoads = 1,
                ),
            )
            val blocker = cacheKey(
                "many-admission-blocker-$suffix",
                returns<String>(),
                exact(manyAdmissionId),
                loadConcurrency = group,
            )
            val targetName = "many-admission-target-$suffix"
            val target = cacheKey(
                targetName,
                returns<String>(),
                exact(manyAdmissionId),
                loadConcurrency = group,
            )
            val blockerStarted = CompletableDeferred<Unit>()
            val targetQueued = CompletableDeferred<Unit>()
            val foregroundStarted = CompletableDeferred<Unit>()
            val releaseBlocker = CompletableDeferred<Unit>()
            val cache = Kacheable(
                store = InMemoryKacheableStore(),
                defaultResilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Local),
                backgroundScope = this,
                telemetry = ManyAdmissionTelemetry(targetName, targetQueued),
            )

            try {
                assertEquals(
                    "blocker-fallback",
                    cache.cache(
                        blocker(1),
                        missPolicy = CacheMissPolicy.loadInBackground { "blocker-fallback" },
                    ) {
                        blockerStarted.complete(Unit)
                        releaseBlocker.await()
                        "blocker"
                    },
                )
                blockerStarted.await()

                assertEquals(
                    mapOf(1 to "target-fallback"),
                    cache.cache(
                        target.many(1),
                        missPolicy = CacheMissPolicy.loadInBackground { "target-fallback" },
                    ) { keys, _ ->
                        keys.associateWith { "background-$it" }
                    },
                )
                targetQueued.await()

                val foreground = async(start = CoroutineStart.UNDISPATCHED) {
                    when (caller) {
                        ForegroundCaller.Scalar -> mapOf(
                            1 to cache.cache(target(1)) {
                                foregroundStarted.complete(Unit)
                                "foreground-1"
                            },
                        )

                        ForegroundCaller.Batch -> cache.cache(target.many(1)) { keys, _ ->
                            foregroundStarted.complete(Unit)
                            keys.associateWith { "foreground-$it" }
                        }
                    }
                }

                assertTrue(
                    foregroundStarted.isCompleted,
                    "The admitted foreground caller joined a batch that was still waiting for background capacity.",
                )
                assertEquals(mapOf(1 to "foreground-1"), foreground.await())
            } finally {
                releaseBlocker.complete(Unit)
            }
        }
    }
}

private enum class ForegroundCaller {
    Scalar,
    Batch,
}

private class ManyAdmissionTelemetry(
    private val targetCacheName: String,
    private val queued: CompletableDeferred<Unit>,
) : CacheTelemetry {
    override fun begin(operation: CacheOperation): CacheObservation =
        object : CacheObservation {
            override fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
                if (
                    operation.cacheName == targetCacheName &&
                    reason == CacheWaitReason.ConcurrencyLimit
                ) {
                    queued.complete(Unit)
                }
            }
        }
}
