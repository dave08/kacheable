package kacheable

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.cache
import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.event.command.CommandListener
import io.lettuce.core.event.command.CommandStartedEvent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val RedisPartitionCommandSpec by testSuite {
    testWithRedis(
        "suspending guarded scalar hot hit uses one Redis command",
        imageName = REDIS_PARTITION_COMMAND_IMAGE,
    ) {
        RedisPartitionCommandCapture(this).use { capture ->
            val cache = capture.suspendingPartitionCache(withAdmission = false)
            cache.cache(partitionCommandPages(PARTITION, WARM_ENTRY)) { _, _ -> "warm" }
            cache.cache(partitionCommandPages(PARTITION, TARGET_ENTRY)) { _, _ -> "target" }
            capture.clear()

            val result = cache.cache(partitionCommandPages(PARTITION, TARGET_ENTRY)) {
                error("Hot guarded entry must not invoke its loader")
            }

            assertEquals("target", result)
            capture.assertHotRead()
        }
    }

    testWithRedis(
        "blocking guarded scalar hot hit uses one Redis command",
        imageName = REDIS_PARTITION_COMMAND_IMAGE,
    ) {
        RedisPartitionCommandCapture(this).use { capture ->
            val cache = capture.blockingPartitionCache()
            cache.cache(partitionCommandPages(PARTITION, WARM_ENTRY)) { _, _ -> "warm" }
            cache.cache(partitionCommandPages(PARTITION, TARGET_ENTRY)) { _, _ -> "target" }
            capture.clear()

            val result = cache.cache(partitionCommandPages(PARTITION, TARGET_ENTRY)) {
                error("Hot guarded entry must not invoke its loader")
            }

            assertEquals("target", result)
            capture.assertHotRead()
        }
    }

    testWithRedis(
        "suspending guarded selected hot hit uses one Redis command",
        imageName = REDIS_PARTITION_COMMAND_IMAGE,
    ) {
        RedisPartitionCommandCapture(this).use { capture ->
            val cache = capture.suspendingPartitionCache(withAdmission = false)
            cache.cache(partitionCommandPages.many(PARTITION, listOf(WARM_ENTRY))) { keys, _ ->
                keys.associateWith { "warm" }
            }
            cache.cache(partitionCommandPages.many(PARTITION, listOf(TARGET_ENTRY))) { keys, _ ->
                keys.associateWith { "target" }
            }
            capture.clear()

            val result = cache.cache(partitionCommandPages.many(PARTITION, listOf(TARGET_ENTRY))) { _, _ ->
                error("Hot guarded selection must not invoke its loader")
            }

            assertEquals(mapOf(TARGET_ENTRY to "target"), result)
            capture.assertHotRead()
        }
    }

    testWithRedis(
        "blocking guarded selected hot hit uses one Redis command",
        imageName = REDIS_PARTITION_COMMAND_IMAGE,
    ) {
        RedisPartitionCommandCapture(this).use { capture ->
            val cache = capture.blockingPartitionCache()
            cache.cache(partitionCommandPages.many(PARTITION, listOf(WARM_ENTRY))) { keys, _ ->
                keys.associateWith { "warm" }
            }
            cache.cache(partitionCommandPages.many(PARTITION, listOf(TARGET_ENTRY))) { keys, _ ->
                keys.associateWith { "target" }
            }
            capture.clear()

            val result = cache.cache(partitionCommandPages.many(PARTITION, listOf(TARGET_ENTRY))) { _, _ ->
                error("Hot guarded selection must not invoke its loader")
            }

            assertEquals(mapOf(TARGET_ENTRY to "target"), result)
            capture.assertHotRead()
        }
    }

    testWithRedis(
        "suspending guarded scalar cold load without admission uses six Redis commands",
        imageName = REDIS_PARTITION_COMMAND_IMAGE,
    ) {
        RedisPartitionCommandCapture(this).use { capture ->
            val cache = capture.suspendingPartitionCache(withAdmission = false)
            cache.cache(partitionCommandPages(PARTITION, WARM_ENTRY)) { _, _ -> "warm" }
            capture.clear()
            var loads = 0

            val result = cache.cache(partitionCommandPages(PARTITION, TARGET_ENTRY)) { key, _ ->
                loads++
                "value-$key"
            }

            assertEquals("value-$TARGET_ENTRY", result)
            assertEquals(1, loads, "The command-budget entry must begin missing")
            capture.assertColdLoadBudget(expectedTotal = ON_DEMAND_COLD_COMMANDS)
        }
    }

    testWithRedis(
        "suspending guarded scalar cold load with admission uses six Redis commands",
        imageName = REDIS_PARTITION_COMMAND_IMAGE,
    ) {
        RedisPartitionCommandCapture(this).use { capture ->
            val cache = capture.suspendingPartitionCache(withAdmission = true)
            cache.cache(partitionCommandPages(PARTITION, WARM_ENTRY)) { _, _ -> "warm" }
            capture.clear()
            var loads = 0

            val result = cache.cache(partitionCommandPages(PARTITION, TARGET_ENTRY)) { key, _ ->
                loads++
                "value-$key"
            }

            assertEquals("value-$TARGET_ENTRY", result)
            assertEquals(1, loads, "The command-budget entry must begin missing")
            capture.assertColdLoadBudget(expectedTotal = ON_DEMAND_COLD_COMMANDS)
        }
    }

    testWithRedis(
        "suspending guarded sequential first chunk cold load with admission uses six Redis commands",
        imageName = REDIS_PARTITION_COMMAND_IMAGE,
    ) {
        RedisPartitionCommandCapture(this).use { capture ->
            val cache = capture.suspendingSequentialPartitionCache()
            cache.cache(sequentialPartitionCommandPages(WARM_PARTITION, FIRST_CHUNK)) { _, _ -> "warm" }
            capture.clear()
            var loads = 0

            val result = cache.cache(sequentialPartitionCommandPages(PARTITION, FIRST_CHUNK)) { key, _ ->
                loads++
                "value-$key"
            }

            assertEquals("value-$FIRST_CHUNK", result)
            assertEquals(1, loads, "The sequential command-budget partition must begin missing")
            capture.assertColdLoadBudget(expectedTotal = SEQUENTIAL_FIRST_CHUNK_COLD_COMMANDS)
        }
    }

    testWithRedis(
        "suspending guarded selected cold load without admission uses six Redis commands",
        imageName = REDIS_PARTITION_COMMAND_IMAGE,
    ) {
        RedisPartitionCommandCapture(this).use { capture ->
            val cache = capture.suspendingPartitionCache(withAdmission = false)
            cache.cache(partitionCommandPages.many(PARTITION, WARM_SELECTION)) { keys, _ ->
                keys.associateWith { "warm" }
            }
            capture.clear()
            var loads = 0

            val result = cache.cache(partitionCommandPages.many(PARTITION, TARGET_SELECTION)) { keys, _ ->
                loads++
                keys.associateWith { "value-$it" }
            }

            assertEquals(TARGET_SELECTION.associateWith { "value-$it" }, result)
            assertEquals(1, loads, "The command-budget selection must begin missing")
            capture.assertColdLoadBudget(expectedTotal = ON_DEMAND_COLD_COMMANDS)
        }
    }

    testWithRedis(
        "suspending guarded selected cold load with admission uses six Redis commands",
        imageName = REDIS_PARTITION_COMMAND_IMAGE,
    ) {
        RedisPartitionCommandCapture(this).use { capture ->
            val cache = capture.suspendingPartitionCache(withAdmission = true)
            cache.cache(partitionCommandPages.many(PARTITION, WARM_SELECTION)) { keys, _ ->
                keys.associateWith { "warm" }
            }
            capture.clear()
            var loads = 0

            val result = cache.cache(partitionCommandPages.many(PARTITION, TARGET_SELECTION)) { keys, _ ->
                loads++
                keys.associateWith { "value-$it" }
            }

            assertEquals(TARGET_SELECTION.associateWith { "value-$it" }, result)
            assertEquals(1, loads, "The command-budget selection must begin missing")
            capture.assertColdLoadBudget(expectedTotal = ON_DEMAND_COLD_COMMANDS)
        }
    }
}

private const val REDIS_PARTITION_COMMAND_IMAGE = "redis:7-alpine"
private const val PARTITION_COMMAND_CACHE = "partition-command-budget"
private const val SEQUENTIAL_PARTITION_COMMAND_CACHE = "sequential-partition-command-budget"
private const val PARTITION = "delivery"
private const val WARM_PARTITION = "warmup"
private const val WARM_ENTRY = 0
private const val TARGET_ENTRY = 1
private const val FIRST_CHUNK = 0
private const val ON_DEMAND_COLD_COMMANDS = 6
private const val SEQUENTIAL_FIRST_CHUNK_COLD_COMMANDS = 6
private val WARM_SELECTION = listOf(WARM_ENTRY, -1)
private val TARGET_SELECTION = listOf(TARGET_ENTRY, 2)

private val partitionCommandPages = cacheKey(
    PARTITION_COMMAND_CACHE,
    returns<String>(),
    key = partitioned(
        partition = keyPart<String>("partition"),
        key = keyPart<Int>("entry"),
    ),
)

private val sequentialPartitionCommandPages = cacheKey(
    SEQUENTIAL_PARTITION_COMMAND_CACHE,
    returns<String>(),
    key = partitioned(
        partition = keyPart<String>("partition"),
        key = enumerableKeyPart<Int>("entry"),
    ),
)

private fun RedisPartitionCommandCapture.suspendingPartitionCache(withAdmission: Boolean): Kacheable = Kacheable(
    store = RedisKacheableStore(connection),
    configs = mapOf(PARTITION_COMMAND_CACHE to guardedPartitionConfig(withRedisSingleFlight = true)),
    loadConcurrency = if (withAdmission) {
        LoadConcurrencySettings(default = LoadConcurrencyConfig(maxConcurrentLoads = 1))
    } else {
        LoadConcurrencySettings()
    },
)

private fun RedisPartitionCommandCapture.suspendingSequentialPartitionCache(): Kacheable = Kacheable(
    store = RedisKacheableStore(connection),
    configs = mapOf(
        SEQUENTIAL_PARTITION_COMMAND_CACHE to CacheConfig(
            name = SEQUENTIAL_PARTITION_COMMAND_CACHE,
            partition = CachePartitionPolicy.SequentialFrom(first = FIRST_CHUNK),
            resilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Redis),
        ),
    ),
    loadConcurrency = LoadConcurrencySettings(default = LoadConcurrencyConfig(maxConcurrentLoads = 1)),
)

private fun RedisPartitionCommandCapture.blockingPartitionCache(): BlockingKacheable = BlockingKacheable(
    store = RedisBlockingKacheableStore(connection),
    configs = mapOf(PARTITION_COMMAND_CACHE to guardedPartitionConfig(withRedisSingleFlight = false)),
)

private fun guardedPartitionConfig(withRedisSingleFlight: Boolean) = CacheConfig(
    name = PARTITION_COMMAND_CACHE,
    partition = CachePartitionPolicy.OnDemand(
        publication = CachePublication.IfAbsent,
        coordination = CacheCoordination.Partition,
    ),
    resilience = CacheResilienceConfig(
        singleFlight = if (withRedisSingleFlight) SingleFlightMode.Redis else SingleFlightMode.None,
    ),
)

private class RedisPartitionCommandCapture(
    private val redis: RedisFixture,
) : CommandListener, AutoCloseable {
    private val started = CopyOnWriteArrayList<String>()
    private val names: List<String> get() = started.toList()
    val connection: StatefulRedisConnection<String, String>

    init {
        redis.addCommandListener(this)
        // Lettuce attaches client listeners to connections created after registration.
        connection = redis.newConnection()
    }

    override fun commandStarted(event: CommandStartedEvent) {
        started += event.command.type.name().lowercase()
    }

    fun clear() = started.clear()

    fun assertHotRead() {
        assertEquals(
            listOf("evalsha"),
            names,
            "A guarded hot hit should atomically open and read the selected field once; commands=$names",
        )
    }

    fun assertColdLoadBudget(expectedTotal: Int) {
        val observed = names
        assertEquals(1, observed.count { it == "set" }, "Cold load must acquire one Redis lease; commands=$observed")
        assertTrue(
            observed.all { it == "evalsha" || it == "set" },
            "Cold load should use only warmed Lua operations and one lease SET; commands=$observed",
        )
        assertTrue(
            observed.firstOrNull() == "evalsha" && observed.lastOrNull() == "evalsha",
            "Cold load must include the initial guarded read and final generation validation; commands=$observed",
        )
        assertEquals(
            expectedTotal - 1,
            observed.count { it == "evalsha" },
            "Cold load used an unexpected EVALSHA count; commands=$observed",
        )
        assertEquals(
            expectedTotal,
            observed.size,
            "Cold uncontended guarded load used an unexpected Redis command count; commands=$observed",
        )
    }

    override fun close() {
        connection.close()
        redis.removeCommandListener(this)
    }
}
