package kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheObservation
import com.github.dave08.kacheable.CacheOperation
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.CacheTelemetry
import com.github.dave08.kacheable.CacheLoadRole
import com.github.dave08.kacheable.CacheWaitReason
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.LoadConcurrencyConfig
import com.github.dave08.kacheable.NoopCacheTelemetry
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.loadConcurrencyGroup
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.redis.RedisKacheableStore
import com.github.dave08.kacheable.returns
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val FALLBACK_VALUE = "pending"
private const val LOADED_VALUE = "value"

private val backgroundEntry = keyPart<String>("entry")
private val backgroundValue = cacheKey(
    "background-single-flight",
    returns<String>(),
    key = partitioned(key = backgroundEntry),
)
private val admissionGroup = loadConcurrencyGroup(
    "redis-admission-order",
    LoadConcurrencyConfig(maxConcurrentLoads = 1, maxConcurrentBackgroundLoads = 1),
)
private val admissionBlocker = cacheKey(
    "redis-admission-blocker",
    returns<String>(),
    key = partitioned(key = backgroundEntry),
    loadConcurrency = admissionGroup,
)
private val admissionTarget = cacheKey(
    "redis-admission-target",
    returns<String>(),
    key = partitioned(key = backgroundEntry),
    loadConcurrency = admissionGroup,
)

val RedisBackgroundSingleFlightSpec by testSuite(
    testConfig = TestConfig.testScope(isEnabled = false),
) {
    testWithRedis(
        name = "background misses share one distributed load across cache instances",
        imageName = "redis:7-alpine",
    ) {
        newConnection().use { secondConnection ->
            val loaderCalls = AtomicInteger()
            val firstInstance = backgroundCache(connection)
            val secondInstance = backgroundCache(secondConnection)

            val fallbacks = concurrently(firstInstance, secondInstance) { cache ->
                cache.cache(
                    backgroundValue("same-entry"),
                    missPolicy = CacheMissPolicy.loadInBackground { FALLBACK_VALUE },
                ) {
                    loaderCalls.incrementAndGet()
                    delay(100.milliseconds)
                    LOADED_VALUE
                }
            }

            assertEquals(listOf(FALLBACK_VALUE, FALLBACK_VALUE), fallbacks)
            firstInstance.awaitCachedValue()
            assertEquals(1, loaderCalls.get())
        }
    }

    testWithRedis(
        name = "queued background work does not claim Redis leadership before admission",
        imageName = "redis:7-alpine",
    ) {
        newConnection().use { secondConnection ->
            coroutineScope {
                val backgroundQueued = CompletableDeferred<Unit>()
                val blockerStarted = CompletableDeferred<Unit>()
                val releaseBlocker = CompletableDeferred<Unit>()
                val loaderCalls = AtomicInteger()
                val firstInstance = admissionCache(
                    connection,
                    backgroundScope = this,
                    telemetry = AdmissionWaitSignalTelemetry(backgroundQueued),
                )
                val secondInstance = admissionCache(secondConnection, backgroundScope = this)

                val blocker = async(start = CoroutineStart.UNDISPATCHED) {
                    firstInstance.cache(admissionBlocker("blocker")) {
                        blockerStarted.complete(Unit)
                        releaseBlocker.await()
                        "blocker"
                    }
                }
                blockerStarted.await()
                assertEquals(
                    FALLBACK_VALUE,
                    firstInstance.cache(
                        admissionTarget("same-entry"),
                        missPolicy = CacheMissPolicy.loadInBackground { FALLBACK_VALUE },
                    ) {
                        loaderCalls.incrementAndGet()
                        "background"
                    },
                )
                backgroundQueued.await()

                val foreground = async(start = CoroutineStart.UNDISPATCHED) {
                    secondInstance.cache(admissionTarget("same-entry")) {
                        loaderCalls.incrementAndGet()
                        "foreground"
                    }
                }

                assertEquals("foreground", foreground.await())
                releaseBlocker.complete(Unit)
                assertEquals("blocker", blocker.await())
                assertEquals(1, loaderCalls.get())
            }
        }
    }

    testWithRedis(
        name = "Redis single-flight joiner releases local admission while waiting",
        imageName = "redis:7-alpine",
    ) {
        newConnection().use { secondConnection ->
            coroutineScope {
                val leaderStarted = CompletableDeferred<Unit>()
                val releaseLeader = CompletableDeferred<Unit>()
                val joinerWaiting = CompletableDeferred<Unit>()
                val unrelatedStarted = CompletableDeferred<Unit>()
                val firstInstance = admissionCache(connection, backgroundScope = this)
                val secondInstance = admissionCache(
                    secondConnection,
                    backgroundScope = this,
                    telemetry = RedisJoinWaitSignalTelemetry(joinerWaiting),
                )

                val leader = async(start = CoroutineStart.UNDISPATCHED) {
                    firstInstance.cache(admissionTarget("joined-entry")) {
                        leaderStarted.complete(Unit)
                        releaseLeader.await()
                        "leader"
                    }
                }
                leaderStarted.await()
                val joiner = async(start = CoroutineStart.UNDISPATCHED) {
                    secondInstance.cache(admissionTarget("joined-entry")) {
                        error("joiner must not execute the loader")
                    }
                }
                joinerWaiting.await()

                val unrelated = async(start = CoroutineStart.UNDISPATCHED) {
                    secondInstance.cache(admissionBlocker("unrelated-entry")) {
                        unrelatedStarted.complete(Unit)
                        "unrelated"
                    }
                }

                unrelatedStarted.await()
                assertEquals("unrelated", unrelated.await())
                releaseLeader.complete(Unit)
                assertEquals("leader", leader.await())
                assertEquals("leader", joiner.await())
            }
        }
    }
}

private fun backgroundCache(connection: StatefulRedisConnection<String, String>) =
    Kacheable(
        store = RedisKacheableStore(connection),
        configs = mapOf(
            "background-single-flight" to CacheConfig(
                name = "background-single-flight",
                resilience = CacheResilienceConfig(
                    singleFlight = SingleFlightMode.Redis,
                    loadTimeout = 2.seconds,
                ),
            ),
        )
    )

private fun admissionCache(
    connection: StatefulRedisConnection<String, String>,
    backgroundScope: CoroutineScope,
    telemetry: CacheTelemetry = NoopCacheTelemetry,
): Kacheable =
    Kacheable(
        store = RedisKacheableStore(connection),
        configs = mapOf(
            "redis-admission-target" to CacheConfig(
                name = "redis-admission-target",
                resilience = CacheResilienceConfig(
                    singleFlight = SingleFlightMode.Redis,
                    loadTimeout = 2.seconds,
                ),
            ),
        ),
        backgroundScope = backgroundScope,
        telemetry = telemetry,
    )

private class AdmissionWaitSignalTelemetry(
    private val queued: CompletableDeferred<Unit>,
) : CacheTelemetry {
    override fun begin(operation: CacheOperation): CacheObservation =
        object : CacheObservation {
            override fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
                if (
                    operation.cacheName == "redis-admission-target" &&
                    reason == CacheWaitReason.ConcurrencyLimit
                ) {
                    queued.complete(Unit)
                }
            }
        }
}

private class RedisJoinWaitSignalTelemetry(
    private val waiting: CompletableDeferred<Unit>,
) : CacheTelemetry {
    override fun begin(operation: CacheOperation): CacheObservation =
        object : CacheObservation {
            override fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
                if (
                    operation.cacheName == "redis-admission-target" &&
                    reason == CacheWaitReason.RedisSingleFlight
                ) {
                    waiting.complete(Unit)
                }
            }
        }
}

private suspend fun Kacheable.awaitCachedValue() {
    val cached = withTimeout(5.seconds) {
        while (true) {
            val value = cache(
                backgroundValue("same-entry"),
                cacheIf = { false },
            ) { "not-cached" }

            if (value != "not-cached") return@withTimeout value
            delay(25.milliseconds)
        }

        error("Unreachable")
    }

    assertEquals(LOADED_VALUE, cached)
}

private suspend fun <T, R> concurrently(
    vararg values: T,
    block: suspend (T) -> R,
): List<R> = coroutineScope {
    val start = CompletableDeferred<Unit>()
    values.map { value ->
        async {
            start.await()
            block(value)
        }
    }.also { start.complete(Unit) }.awaitAll()
}
