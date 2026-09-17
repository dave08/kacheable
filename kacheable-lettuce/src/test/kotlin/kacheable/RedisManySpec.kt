package kacheable

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.redis.RedisKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val RedisManySpec by testSuite {
    testWithRedis("Redis selected values retain partial successes and negative entries with independent expiry", imageName = "redis:7-alpine") {
        val key = cacheKey("redis-many", returns<String?>(), exact(keyPart<Int>("id")))
        val cache = Kacheable(RedisKacheableStore(connection), configs = mapOf(
            "redis-many" to CacheConfig("redis-many", ExpiryType.after_write, 5.minutes, nullPlaceholder = "<null>"),
        ))
        val first = cache(key.many(1, 2, 3)) { _, _ -> mapOf(1 to "one", 2 to null) }
        var loaded = emptyList<Int>()
        val second = cache(key.many(2, 3, 1)) { keys, _ -> loaded = keys; mapOf(3 to "three") }

        assertEquals(mapOf(1 to "one", 2 to null), first)
        assertEquals(listOf(3), loaded)
        assertEquals(mapOf(2 to null, 3 to "three", 1 to "one"), second)
        assertEquals("one", cache(key(1)) { error("scalar caller missed batch publication") })
        assertNull(cache(key(2)) { error("cached null was lost") })
        for (id in 1..3) assertTrue(commands.pttl("redis-many:$id") > 0)
    }

    testWithRedis("Redis overlapping selected loads publish owned entries before waiting for another batch", imageName = "redis:7-alpine") {
        newConnection().use { other ->
            val key = cacheKey("redis-many-flight", returns<String>(), exact(keyPart<Int>("id")))
            val configs = mapOf("redis-many-flight" to CacheConfig("redis-many-flight",
                resilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Redis)))
            val firstCache = Kacheable(RedisKacheableStore(connection), configs)
            val secondCache = Kacheable(RedisKacheableStore(other), configs)
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(10.seconds) {
                    coroutineScope {
                        val firstStarted = CompletableDeferred<Unit>()
                        val finishFirst = CompletableDeferred<Unit>()
                        val secondLoaded = CompletableDeferred<List<Int>>()
                        val first = async {
                            firstCache(key.many(1, 2)) { keys, _ ->
                                assertEquals(listOf(1, 2), keys)
                                firstStarted.complete(Unit)
                                finishFirst.await()
                                keys.associateWith { "value-$it" }
                            }
                        }
                        firstStarted.await()
                        val second = async {
                            secondCache(key.many(2, 3)) { keys, _ ->
                                secondLoaded.complete(keys)
                                keys.associateWith { "value-$it" }
                            }
                        }
                        assertEquals(listOf(3), secondLoaded.await())
                        finishFirst.complete(Unit)
                        assertEquals(mapOf(1 to "value-1", 2 to "value-2"), first.await())
                        assertEquals(mapOf(2 to "value-2", 3 to "value-3"), second.await())
                    }
                }
            }
        }
    }
}
