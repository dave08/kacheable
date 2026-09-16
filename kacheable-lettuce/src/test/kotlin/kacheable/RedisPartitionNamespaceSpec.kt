package kacheable

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.cache
import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val RedisPartitionNamespaceSpec by testSuite {
    testWithRedis("Redis guarded partition invalidation leaves its ordinary namesake and other partitions intact") {
        val caches = PartitionNamespaceCaches(this)
        caches.seed("a")
        caches.seed("b")

        caches.guarded.invalidate(partitionPages.partition("a"))

        assertEquals("new", caches.guarded.cache(partitionPages("a", 1)) { "new" })
        assertEquals("guarded-b", caches.guarded.cache(partitionPages("b", 1)) { error("Other partition removed") })
        assertEquals("ordinary-a", caches.ordinary.cache(partitionPages("a", 1)) { error("Ordinary data removed") })
    }

    testWithRedis("Redis ordinary family invalidation leaves guarded namesakes intact") {
        val caches = PartitionNamespaceCaches(this)
        caches.seed("a")

        caches.ordinary.invalidate(partitionPages.all())

        assertEquals("new", caches.ordinary.cache(partitionPages("a", 1)) { "new" })
        assertEquals("guarded-a", caches.guarded.cache(partitionPages("a", 1)) { error("Guarded data removed") })
    }

    testWithRedis("Redis guarded family invalidation includes the hash without an outer partition") {
        val caches = PartitionNamespaceCaches(this)
        caches.ordinary.cache(rootPages(1)) { "ordinary" }
        caches.guarded.cache(rootPages(1)) { "guarded" }

        caches.guarded.invalidate(rootPages.all())

        assertEquals("new", caches.guarded.cache(rootPages(1)) { "new" })
        assertEquals("ordinary", caches.ordinary.cache(rootPages(1)) { error("Ordinary root removed") })
    }

    testWithRedis("blocking Redis guarded family invalidation preserves ordinary namesakes") {
        val store = RedisBlockingKacheableStore(connection)
        val ordinary = BlockingKacheable(store)
        val guarded = BlockingKacheable(store, configs = guardedPageConfigs)
        ordinary.cache(partitionPages("a", 1)) { "ordinary" }
        guarded.cache(partitionPages("a", 1)) { "guarded" }

        guarded.invalidate(partitionPages.all())

        assertEquals("new", guarded.cache(partitionPages("a", 1)) { "new" })
        assertEquals("ordinary", ordinary.cache(partitionPages("a", 1)) { error("Ordinary data removed") })
    }
}

private val partitionPages = cacheKey(
    "namespace-pages", returns<String>(),
    key = partitioned(keyPart<String>("group"), keyPart<Int>("page")),
)
private val rootPages = cacheKey("namespace-pages", returns<String>(), key = partitioned(key = keyPart<Int>("page")))
private val guardedPageConfigs = mapOf(
    "namespace-pages" to CacheConfig("namespace-pages", partition = CachePartitionPolicy.OnDemand()),
)

private class PartitionNamespaceCaches(redis: RedisFixture) {
    private val store = RedisKacheableStore(redis.connection)
    val ordinary = Kacheable(store)
    val guarded = Kacheable(store, configs = guardedPageConfigs)

    suspend fun seed(group: String) {
        ordinary.cache(partitionPages(group, 1)) { "ordinary-$group" }
        guarded.cache(partitionPages(group, 1)) { "guarded-$group" }
    }
}
