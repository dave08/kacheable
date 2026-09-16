import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisKacheableStore
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class Window(val offset: Int, val limit: Int)

@Serializable
data class Chunk(val ids: List<Int>)

fun main() = runBlocking {
    ordinaryCacheRetainsItsValue()
    enumerablePartitionRetainsTheLogicalKey()
    println("Published API smoke checks passed.")
}

private suspend fun ordinaryCacheRetainsItsValue() {
    val cache = Kacheable(InMemoryKacheableStore())
    val key = cacheKey("value", returns<Int>(), key = exact(keyPart<Int>("id")))
    check(cache(key(1)) { 42 } == 42)
    check(cache(key(1)) { error("Unexpected cache miss") } == 42)
}

private suspend fun enumerablePartitionRetainsTheLogicalKey() {
    val windows = cacheKey(
        "windows", returns<Chunk>(),
        key = partitioned(
            partition = keyPart<String>("delivery"),
            key = enumerableKeyPart<Window>("window", Window::offset, Window::limit),
        ),
    )
    val cache = Kacheable(
        InMemoryKacheableStore(),
        configs = mapOf("windows" to CacheConfig(
            name = "windows", partition = CachePartitionPolicy.OnDemand(),
        )),
    )
    val first = Window(0, 10)
    cache(windows("D55", first)) { _, _ -> Chunk(listOf(1)) }
    val second = cache(windows("D55", Window(10, 10))) { _, partition ->
        check(partition.keys() == setOf(first))
        check(partition.entries()[first] == Chunk(listOf(1)))
        Chunk(listOf(2))
    }
    check(second == Chunk(listOf(2)))
}

fun blockingValue(cache: BlockingKacheable): Int {
    val key = cacheKey("blocking", returns<Int>(), key = exact(keyPart<Int>("id")))
    return cache(key(1)) { 42 }
}

// Compiles the documented Redis factory path without requiring a server for the smoke run.
fun redisCache(connection: StatefulRedisConnection<String, String>): Kacheable =
    Kacheable(RedisKacheableStore(connection))

fun blockingRedisCache(connection: StatefulRedisConnection<String, String>): BlockingKacheable =
    BlockingKacheable(RedisBlockingKacheableStore(connection))
