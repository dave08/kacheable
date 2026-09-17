package kacheable

import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.event.command.CommandListener
import io.lettuce.core.event.command.CommandStartedEvent
import io.lettuce.core.api.StatefulRedisConnection
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

val RedisBatchReadSpec by testSuite {
    for (api in BatchReadApi.entries) {
        testWithRedis("$api string batch reads use MGET and preserve requested key associations", imageName = REDIS_BATCH_IMAGE) {
            commands.mset(mapOf("song:1" to "first", "song:2" to "second"))
            commands.configResetstat()

            val result = api.getValues(this, listOf("song:2", "song:missing", "song:1"))

            assertEquals(mapOf("song:2" to "second", "song:1" to "first"), result)
            assertEquals(listOf("song:2", "song:1"), result.keys.toList())
            assertCommandCalls("mget", 1)
            assertCommandNotCalled("get")
        }

        testWithRedis("$api hash batch reads use HMGET and preserve requested field associations", imageName = REDIS_BATCH_IMAGE) {
            commands.hset("songs", mapOf("1" to "first", "2" to "second"))
            commands.configResetstat()

            val result = api.getHashValues(this, "songs", listOf("2", "missing", "1"))

            assertEquals(mapOf("2" to "second", "1" to "first"), result)
            assertEquals(listOf("2", "1"), result.keys.toList())
            assertCommandCalls("hmget", 1)
            assertCommandNotCalled("hget")
        }

        testWithRedis("$api set membership batch reads use SMISMEMBER and return matching requested members", imageName = REDIS_BATCH_IMAGE) {
            commands.sadd("liked", "1", "2")
            commands.configResetstat()

            val result = api.areSetMembers(this, "liked", listOf("2", "missing", "1"))

            assertEquals(setOf("2", "1"), result)
            assertEquals(listOf("2", "1"), result.toList())
            assertCommandCalls("smismember", 1)
            assertCommandNotCalled("sismember")
        }

        testWithRedis("$api refreshing string batch reads omit misses and refresh expiry for hits", imageName = REDIS_BATCH_IMAGE) {
            val binarySafe = "\u0000שלום🙂"
            commands.mset(mapOf(
                "warm" to "warm",
                "song:1" to "first",
                "song:2" to "second",
                "song:empty" to "",
                "song:binary" to binarySafe,
            ))
            val (result, names) = withObservedCommands { connection, commandsSent ->
                val store = api.refreshingStore(connection)
                store.getValuesRefreshingExpire(listOf("warm"), 1.minutes)
                commandsSent.clear()
                store.getValuesRefreshingExpire(
                    listOf("song:2", "song:missing", "song:1", "song:2", "song:empty", "song:binary"),
                    5.minutes,
                )
            }

            assertEquals(
                mapOf(
                    "song:2" to "second",
                    "song:1" to "first",
                    "song:empty" to "",
                    "song:binary" to binarySafe,
                ),
                result,
            )
            assertEquals(listOf("song:2", "song:1", "song:empty", "song:binary"), result.keys.toList())
            assertEquals(-2L, commands.pttl("song:missing"))
            listOf("song:1", "song:2", "song:empty", "song:binary").forEach { key ->
                assertTrue(commands.pttl(key) in 1..5.minutes.inWholeMilliseconds, "$key TTL was not refreshed")
            }
            assertEquals(listOf("evalsha"), names)
        }

        testWithRedis("$api refreshing string batch rejects a wrong type without refreshing earlier keys", imageName = REDIS_BATCH_IMAGE) {
            commands.set("warm", "warm")
            commands.set("ordinary", "value")
            commands.pexpire("ordinary", 10.minutes.inWholeMilliseconds)
            commands.hset("wrong-type", "field", "value")
            val (_, names) = withObservedCommands { connection, commandsSent ->
                val store = api.refreshingStore(connection)
                store.getValuesRefreshingExpire(listOf("warm"), 1.minutes)
                commandsSent.clear()
                assertFailsWith<RedisCommandExecutionException> {
                    store.getValuesRefreshingExpire(listOf("ordinary", "wrong-type"), 1.minutes)
                }
            }

            assertTrue(commands.pttl("ordinary") > 8.minutes.inWholeMilliseconds)
            assertEquals(listOf("evalsha"), names)
        }

        testWithRedis("$api refreshing string batch preserves zero-expiry return then delete behavior", imageName = REDIS_BATCH_IMAGE) {
            commands.set("zero-expiry", "value")

            val result = withObservedCommands { connection, _ ->
                api.refreshingStore(connection).getValuesRefreshingExpire(listOf("zero-expiry"), Duration.ZERO)
            }.first
            assertEquals(mapOf("zero-expiry" to "value"), result)
            assertEquals(0L, commands.exists("zero-expiry"))
        }

        testWithRedis("$api empty batch reads issue no Redis read commands", imageName = REDIS_BATCH_IMAGE) {
            commands.configResetstat()
            assertEquals(emptyMap(), api.getValues(this, emptyList()))
            assertEquals(emptyMap(), api.getHashValues(this, "songs", emptyList()))
            assertEquals(emptySet(), api.areSetMembers(this, "liked", emptyList()))
            val (result, names) = withObservedCommands { connection, _ ->
                api.refreshingStore(connection).getValuesRefreshingExpire(emptyList(), 1.minutes)
            }

            assertEquals(emptyMap(), result)
            assertEquals(emptyList(), names)
            assertCommandNotCalled("mget")
            assertCommandNotCalled("hmget")
            assertCommandNotCalled("smismember")
            assertCommandNotCalled("getex")
        }

        testWithRedis("$api batch reads reject reserved ordinary namespaces before Redis commands", imageName = REDIS_BATCH_IMAGE) {
            commands.set("ordinary", "value")
            commands.pexpire("ordinary", 10.minutes.inWholeMilliseconds)
            commands.configResetstat()
            assertFailsWith<IllegalArgumentException> {
                api.getValues(this, listOf("ordinary", RESERVED_BATCH_KEY))
            }
            assertFailsWith<IllegalArgumentException> {
                api.getHashValues(this, RESERVED_BATCH_KEY, emptyList())
            }
            assertFailsWith<IllegalArgumentException> {
                api.areSetMembers(this, RESERVED_BATCH_KEY, emptyList())
            }
            val (_, names) = withObservedCommands { connection, _ ->
                assertFailsWith<IllegalArgumentException> {
                    api.refreshingStore(connection).getValuesRefreshingExpire(
                        listOf("ordinary", RESERVED_BATCH_KEY),
                        1.minutes,
                    )
                }
            }

            assertEquals(emptyList(), names)
            assertTrue(commands.pttl("ordinary") > 8.minutes.inWholeMilliseconds)
            assertCommandNotCalled("mget")
            assertCommandNotCalled("hmget")
            assertCommandNotCalled("smismember")
            assertCommandNotCalled("getex")
        }
    }
}

private fun RedisFixture.assertCommandCalls(command: String, calls: Int) {
    val statistics = commands.info("commandstats")
    assertTrue("cmdstat_$command:calls=$calls," in statistics, statistics)
}

private fun RedisFixture.assertCommandNotCalled(command: String) {
    val statistics = commands.info("commandstats")
    assertFalse("cmdstat_$command" in statistics, statistics)
}

private const val REDIS_BATCH_IMAGE = "redis:7-alpine"
private const val RESERVED_BATCH_KEY = "__kacheable:versioned-hash:v1:delivery"

private fun interface RefreshingBatchRead {
    suspend fun getValuesRefreshingExpire(keys: List<String>, expiry: Duration): Map<String, String>
}

private class StartedCommands : CommandListener {
    private val started = CopyOnWriteArrayList<String>()
    val names: List<String> get() = started.toList()

    override fun commandStarted(event: CommandStartedEvent) {
        started += event.command.type.name().lowercase()
    }

    fun clear() = started.clear()
}

private suspend fun <T> RedisFixture.withObservedCommands(
    block: suspend (StatefulRedisConnection<String, String>, StartedCommands) -> T,
): Pair<T, List<String>> {
    val listener = StartedCommands()
    addCommandListener(listener)
    val connection = newConnection()
    return try {
        block(connection, listener) to listener.names
    } finally {
        connection.close()
        removeCommandListener(listener)
    }
}

private enum class BatchReadApi {
    Suspending {
        override suspend fun getValues(redis: RedisFixture, keys: List<String>): Map<String, String> =
            RedisKacheableStore(redis.connection).getValues(keys)

        override suspend fun getHashValues(redis: RedisFixture, key: String, fields: List<String>): Map<String, String> =
            RedisKacheableStore(redis.connection).getHashValues(key, fields)

        override suspend fun areSetMembers(redis: RedisFixture, key: String, members: List<String>): Set<String> =
            RedisKacheableStore(redis.connection).areSetMembers(key, members)

        override fun refreshingStore(connection: StatefulRedisConnection<String, String>): RefreshingBatchRead {
            val store = RedisKacheableStore(connection)
            return RefreshingBatchRead(store::getValuesRefreshingExpire)
        }
    },
    Blocking {
        override suspend fun getValues(redis: RedisFixture, keys: List<String>): Map<String, String> =
            RedisBlockingKacheableStore(redis.connection).getValues(keys)

        override suspend fun getHashValues(redis: RedisFixture, key: String, fields: List<String>): Map<String, String> =
            RedisBlockingKacheableStore(redis.connection).getHashValues(key, fields)

        override suspend fun areSetMembers(redis: RedisFixture, key: String, members: List<String>): Set<String> =
            RedisBlockingKacheableStore(redis.connection).areSetMembers(key, members)

        override fun refreshingStore(connection: StatefulRedisConnection<String, String>): RefreshingBatchRead {
            val store = RedisBlockingKacheableStore(connection)
            return RefreshingBatchRead(store::getValuesRefreshingExpire)
        }
    };

    abstract suspend fun getValues(redis: RedisFixture, keys: List<String>): Map<String, String>
    abstract suspend fun getHashValues(redis: RedisFixture, key: String, fields: List<String>): Map<String, String>
    abstract suspend fun areSetMembers(redis: RedisFixture, key: String, members: List<String>): Set<String>
    abstract fun refreshingStore(connection: StatefulRedisConnection<String, String>): RefreshingBatchRead
}
