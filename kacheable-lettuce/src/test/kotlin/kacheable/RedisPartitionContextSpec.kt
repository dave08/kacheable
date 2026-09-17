package kacheable

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.redis.RedisKacheableStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

val RedisPartitionContextSpec by testSuite {
    testWithRedis("expired Redis partition lease replays dependent loading against a competing sibling publication") {
        RedisPartitionFixture(this).use { subject ->
            coroutineScope {
                val siblingRead = CompletableDeferred<Unit>()
                val releaseLeader = CompletableDeferred<Unit>()
                var attempts = 0
                val dependent = async(start = CoroutineStart.UNDISPATCHED) {
                    subject.first.cache(subject.pages("delivery", 2)) { key, context ->
                        attempts++
                        val sibling = context.entry(1)
                        if (attempts == 1) {
                            siblingRead.complete(Unit)
                            releaseLeader.await()
                        }
                        when (sibling) {
                            CacheEntry.Missing -> "stale-page-$key"
                            is CacheEntry.Present -> "page-$key-from-${sibling.value}"
                        }
                    }
                }
                siblingRead.await()
                subject.expireLeaderLease()
                assertEquals("page-1", subject.second.cache(subject.pages("delivery", 1)) { key, _ -> "page-$key" })
                releaseLeader.complete(Unit)

                assertEquals("page-2-from-page-1", dependent.await())
                assertEquals(2, attempts)
                assertEquals("page-2-from-page-1", subject.second.cache(subject.pages("delivery", 2)) { _, _ -> error("Retried value must be stored") })
            }
        }
    }

    testWithRedis("concurrent Redis partition callers receive their own requested field values") {
        RedisPartitionFixture(this).use { subject ->
            coroutineScope {
                val firstEntered = CompletableDeferred<Unit>()
                val releaseFirst = CompletableDeferred<Unit>()
                val first = async(start = CoroutineStart.UNDISPATCHED) {
                    subject.first.cache(subject.pages("delivery", 1)) { key, _ ->
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                        "page-$key"
                    }
                }
                firstEntered.await()
                val second = async(start = CoroutineStart.UNDISPATCHED) {
                    subject.second.cache(subject.pages("delivery", 2)) { key, context ->
                        val predecessor = context.entry(1) as CacheEntry.Present
                        "page-$key-after-${predecessor.value}"
                    }
                }
                releaseFirst.complete(Unit)

                assertEquals("page-1", first.await())
                assertEquals("page-2-after-page-1", second.await())
            }
        }
    }
}

private class RedisPartitionFixture(private val redis: RedisFixture) : AutoCloseable {
    private val secondConnection = redis.newConnection()
    private val config = CacheConfig(
        name = "pages",
        partition = CachePartitionPolicy.OnDemand(
            coordination = CacheCoordination.Partition,
            publication = CachePublication.IfAbsent,
        ),
        resilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Redis),
        expiry = 5.minutes,
        expiryType = ExpiryType.after_write,
    )
    val first = Kacheable(RedisKacheableStore(redis.connection), mapOf(config.name to config))
    val second = Kacheable(RedisKacheableStore(secondConnection), mapOf(config.name to config))
    val pages = cacheKey("pages", returns<String>(), key = partitioned(partition = keyPart<String>("delivery"), key = keyPart<Int>("page")))

    fun expireLeaderLease() {
        val leaderLease = redis.commands.keys("__kacheable:singleflight:*").single()
        assertEquals(true, redis.commands.pexpire(leaderLease, 0), "Paused leader must own a Redis lease")
    }

    override fun close() { secondConnection.close() }
}
