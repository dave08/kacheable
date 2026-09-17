package kacheable

import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisKacheableStore
import com.github.dave08.kacheable.redis.versionedRedisKey
import com.github.dave08.kacheable.store.HashPublishResult
import com.github.dave08.kacheable.store.HashReadSnapshot
import com.github.dave08.kacheable.store.HashVersion
import de.infix.testBalloon.framework.core.testSuite
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.event.command.CommandListener
import io.lettuce.core.event.command.CommandStartedEvent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

val RedisHashReadSnapshotSpec by testSuite {
    for (api in SnapshotApi.entries) {
        testWithRedis("$api snapshot reads the current generation and selected data in one command") {
            val listener = SnapshotCommands()
            addCommandListener(listener)
            val connection = newConnection()
            try {
                val store = api.store(connection)
                store.readHashSnapshot("warmup", null, emptyList())
                val initial = store.openHash("delivery", 5.minutes)
                val first = store.publishHash(
                    "delivery", initial, "first", "one", 5.minutes, ifAbsent = true, metadata = "typed first",
                ) as HashPublishResult.Published
                val reservedName = "__kacheable_versioned_hash_generation_v1"
                val current = store.publishHash(
                    "delivery", first.version, reservedName, "user value", 5.minutes, ifAbsent = true,
                    metadata = "typed reserved",
                ) as HashPublishResult.Published
                commands.pexpire(versionedRedisKey("delivery"), 60_000)
                listener.clear()

                val snapshot = store.readHashSnapshot(
                    "delivery", 5.minutes, listOf(reservedName, "missing"),
                )

                assertEquals(HashReadSnapshot(current.version, mapOf(reservedName to "user value")), snapshot)
                assertEquals(listOf("evalsha"), listener.names)
                assertTrue(commands.pttl(versionedRedisKey("delivery")) in 1..60_000)
            } finally {
                connection.close()
                removeCommandListener(listener)
            }
        }

        testWithRedis("$api snapshot creates once and preserves generation revision and TTL on later reads") {
            val store = api.store(connection)

            val created = store.readHashSnapshot("delivery", 5.minutes, null)
            assertEquals(0L, created?.version?.revision)
            assertEquals(emptyMap(), created?.values)
            commands.pexpire(versionedRedisKey("delivery"), 60_000)

            val reopened = store.readHashSnapshot("delivery", 5.minutes, null)

            assertEquals(created, reopened)
            assertTrue(commands.pttl(versionedRedisKey("delivery")) in 1..60_000)
        }

        testWithRedis("$api snapshot keeps the guarded namespace and wrong-type checks") {
            commands.set(versionedRedisKey("delivery"), "wrong type")
            val ordinaryBefore = commands.get(versionedRedisKey("delivery"))

            assertFailsWith<RedisCommandExecutionException> {
                api.store(connection).readHashSnapshot("delivery", null, null)
            }

            assertEquals(ordinaryBefore, commands.get(versionedRedisKey("delivery")))
            assertNotEquals("hash", commands.type(versionedRedisKey("delivery")))
        }
    }
}

private interface SnapshotStore {
    suspend fun readHashSnapshot(key: String, expiry: Duration?, fields: List<String>?): HashReadSnapshot?
    suspend fun openHash(key: String, expiry: Duration?): HashVersion
    suspend fun publishHash(
        key: String,
        version: HashVersion,
        field: String,
        value: String,
        expiry: Duration?,
        ifAbsent: Boolean,
        metadata: String? = null,
    ): HashPublishResult
}

private enum class SnapshotApi {
    Suspending {
        override fun store(connection: StatefulRedisConnection<String, String>): SnapshotStore {
            val store = RedisKacheableStore(connection)
            return object : SnapshotStore {
                override suspend fun readHashSnapshot(key: String, expiry: Duration?, fields: List<String>?) =
                    store.readHashSnapshot(key, expiry, fields)

                override suspend fun openHash(key: String, expiry: Duration?) = store.openHash(key, expiry)

                override suspend fun publishHash(
                    key: String,
                    version: HashVersion,
                    field: String,
                    value: String,
                    expiry: Duration?,
                    ifAbsent: Boolean,
                    metadata: String?,
                ) = store.publishHash(key, version, field, value, expiry, ifAbsent, metadata)
            }
        }
    },
    Blocking {
        override fun store(connection: StatefulRedisConnection<String, String>): SnapshotStore {
            val store = RedisBlockingKacheableStore(connection)
            return object : SnapshotStore {
                override suspend fun readHashSnapshot(key: String, expiry: Duration?, fields: List<String>?) =
                    store.readHashSnapshot(key, expiry, fields)

                override suspend fun openHash(key: String, expiry: Duration?) = store.openHash(key, expiry)

                override suspend fun publishHash(
                    key: String,
                    version: HashVersion,
                    field: String,
                    value: String,
                    expiry: Duration?,
                    ifAbsent: Boolean,
                    metadata: String?,
                ) = store.publishHash(key, version, field, value, expiry, ifAbsent, metadata)
            }
        }
    };

    abstract fun store(connection: StatefulRedisConnection<String, String>): SnapshotStore
}

private class SnapshotCommands : CommandListener {
    private val started = CopyOnWriteArrayList<String>()
    val names: List<String> get() = started.toList()

    override fun commandStarted(event: CommandStartedEvent) {
        started += event.command.type.name().lowercase()
    }

    fun clear() = started.clear()
}
