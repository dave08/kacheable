package kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.redis.RedisKacheableStore
import com.github.dave08.kacheable.returns
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.CompletableDeferred
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
