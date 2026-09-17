import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.blocking.BlockingCacheLoadContext
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
    selectedValuesShareScalarEntries()
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
    cache(windows("D55", first)) { key, context ->
        checkAbsent(context, key)
        Chunk(listOf(1))
    }
    val second = cache(windows("D55", Window(10, 10))) { _, partition ->
        check(partition.keys() == setOf(first))
        check(partition.entries()[first] == Chunk(listOf(1)))
        Chunk(listOf(2))
    }
    check(second == Chunk(listOf(2)))
    val third = Window(20, 10)
    val selected = cache(windows.many("D55", listOf(first, third))) { keys, context ->
        check(keys == listOf(third))
        checkAbsent(context, third)
        check(context.keys() == setOf(first, Window(10, 10)))
        mapOf(third to Chunk(listOf(3)))
    }
    check(selected == mapOf(first to Chunk(listOf(1)), third to Chunk(listOf(3))))
}

// The same helper accepts contexts from a scalar partition loader and a selected loader.
private suspend fun <K, V, P : KeyPart<K>> checkAbsent(context: CacheLoadContext<K, V, P>, key: K) {
    check(context.entry(key) == CacheEntry.Missing)
}

private suspend fun selectedValuesShareScalarEntries() {
    val cache: Kacheable = PublishedCacheWrapper(Kacheable(InMemoryKacheableStore()))
    val key = cacheKey("selected", returns<String?>(), exact(keyPart<Int>("id")))
    check(cache(key(1)) { "one" } == "one")
    val selected = cache(key.many(1, 2, 3)) { keys, _ ->
        check(keys == listOf(2, 3))
        mapOf(2 to null)
    }
    check(selected == mapOf(1 to "one", 2 to null))

    val policyKey = cacheKey("selected-policy", returns<String>(), exact(keyPart<Int>("id")))
    val policySelected = cache(policyKey.many(4), missPolicy = CacheMissPolicy.load()) { keys, _ ->
        keys.associateWith { "four" }
    }
    check(policySelected == mapOf(4 to "four"))
}

fun blockingValue(cache: BlockingKacheable): Int {
    val key = cacheKey("blocking", returns<Int>(), key = exact(keyPart<Int>("id")))
    return cache(key(1)) { 42 }
}

fun blockingSelectedValues(cache: BlockingKacheable): Map<Int, Int> {
    val delegated: BlockingKacheable = PublishedBlockingCacheWrapper(cache)
    val key = cacheKey("blocking", returns<Int>(), exact(keyPart<Int>("id")))
    return delegated(key.many(1, 2)) { keys, context ->
        checkBlockingContext(context, keys.first())
        keys.associateWith { 42 }
    }
}

private fun <K, V, P : KeyPart<K>> checkBlockingContext(context: BlockingCacheLoadContext<K, V, P>, key: K) {
    check(context.entry(key) == CacheEntry.Missing)
}

// Compiles the documented Redis factory path without requiring a server for the smoke run.
fun redisCache(connection: StatefulRedisConnection<String, String>): Kacheable =
    Kacheable(RedisKacheableStore(connection))

fun blockingRedisCache(connection: StatefulRedisConnection<String, String>): BlockingKacheable =
    BlockingKacheable(RedisBlockingKacheableStore(connection))

private class PublishedCacheWrapper(cache: Kacheable) : Kacheable by cache

private class PublishedBlockingCacheWrapper(cache: BlockingKacheable) : BlockingKacheable by cache
