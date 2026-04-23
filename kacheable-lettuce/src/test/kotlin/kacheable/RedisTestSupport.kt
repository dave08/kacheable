package kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.RedisKacheableStore
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.RedisBlockingKacheableStore
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.Serializable
import org.testcontainers.containers.GenericContainer

@Serializable
data class Bar(val id: Int, val name: String)

class RedisFixture private constructor(
    private val container: GenericContainer<Nothing>,
    private val client: RedisClient,
    val connection: StatefulRedisConnection<String, String>,
) : AutoCloseable {
    val commands: RedisCommands<String, String>
        get() = connection.sync()

    fun suspendSubject(vararg configs: CacheConfig): SuspendFooFixture =
        SuspendFooFixture(
            cache = Kacheable(RedisKacheableStore(connection), configs.toConfigMap()),
            commands = commands,
        )

    fun blockingSubject(vararg configs: CacheConfig): BlockingFooFixture =
        BlockingFooFixture(
            cache = BlockingKacheable(RedisBlockingKacheableStore(connection), configs.toConfigMap()),
            commands = commands,
        )

    override fun close() {
        connection.close()
        client.shutdown()
        container.stop()
    }

    companion object {
        fun start(): RedisFixture {
            val container = GenericContainer<Nothing>("redis:5.0.3-alpine").apply {
                withExposedPorts(6379)
                start()
            }

            val client = RedisClient.create("redis://${container.host}:${container.getMappedPort(6379)}/0")
            val connection = client.connect()

            return RedisFixture(container, client, connection)
        }
    }
}

class SuspendFooFixture(
    cache: Kacheable,
    val commands: RedisCommands<String, String>,
) {
    val subject = Foo(cache)
}

class BlockingFooFixture(
    cache: BlockingKacheable,
    val commands: RedisCommands<String, String>,
) {
    val subject = BlockingFoo(cache)
}

private fun Array<out CacheConfig>.toConfigMap(): Map<String, CacheConfig> = associateBy { it.name }
