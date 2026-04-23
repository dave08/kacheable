package kacheable

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.Serializable
import org.testcontainers.containers.GenericContainer

@Serializable
data class Bar(val id: Int, val name: String)

suspend fun <T> withRedisConnection(
    block: suspend (StatefulRedisConnection<String, String>) -> T
): T {
    val container = GenericContainer<Nothing>("redis:5.0.3-alpine").apply {
        withExposedPorts(6379)
        start()
    }

    val client = RedisClient.create("redis://${container.host}:${container.getMappedPort(6379)}/0")
    val connection = client.connect()

    return try {
        block(connection)
    } finally {
        connection.close()
        client.shutdown()
        container.stop()
    }
}

fun <T> withRedisConnectionBlocking(
    block: (StatefulRedisConnection<String, String>) -> T
): T {
    val container = GenericContainer<Nothing>("redis:5.0.3-alpine").apply {
        withExposedPorts(6379)
        start()
    }

    val client = RedisClient.create("redis://${container.host}:${container.getMappedPort(6379)}/0")
    val connection = client.connect()

    return try {
        block(connection)
    } finally {
        connection.close()
        client.shutdown()
        container.stop()
    }
}
