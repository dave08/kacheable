package kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheSnapshotPart
import com.github.dave08.kacheable.CacheSnapshotRef
import com.github.dave08.kacheable.CacheSnapshotSlot
import com.github.dave08.kacheable.FileCacheSnapshotStore
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.SnapshotRestore
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.persistentSnapshot
import com.github.dave08.kacheable.redis.RedisKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

val RedisCacheSnapshotStressSpec by testSuite {
    test("restores a heavy indexed cache snapshot into cold Redis without invoking loaders") {
        val redis = RedisFixture.start()
        try {
            val snapshotStore = FileCacheSnapshotStore(
                Files.createTempDirectory(Path.of("/private/tmp"), "kacheable-redis-snapshot-test-"),
            )
            val expectedEntryCount = 1_000

            val warmCache = Kacheable(RedisKacheableStore(redis.connection))
            var warmLoads = 0

            for (artistId in 1..50) {
                for (songId in 1..20) {
                    warmCache.cache(redisArtistSongsCache(artistId, songId)) {
                        warmLoads++
                        Bar(songId, "Artist $artistId Song $songId")
                    }
                }
            }

            assertEquals(expectedEntryCount, warmLoads)

            val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                Kacheable(
                    RedisKacheableStore(redis.connection),
                    configs = mapOf(redisSnapshotConfig(SnapshotRestore.Background, 25.milliseconds).let { it.name to it }),
                    snapshotStore = snapshotStore,
                    backgroundScope = flushScope,
                )

                eventually {
                    snapshotStore.read(CacheSnapshotRef("artist-cache", CacheSnapshotSlot.Latest, CacheSnapshotPart.Manifest))
                        ?.decodeToString()
                        ?.contains(""""entryCount":$expectedEntryCount""") == true
                }
            } finally {
                flushScope.cancel()
            }

            redis.commands.flushdb()

            val coldScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val coldCache = Kacheable(
                    RedisKacheableStore(redis.connection),
                    configs = mapOf(redisSnapshotConfig(SnapshotRestore.Blocking, Duration.ZERO).let { it.name to it }),
                    snapshotStore = snapshotStore,
                    backgroundScope = coldScope,
                )
                var coldLoads = 0

                for (artistId in 1..50) {
                    for (songId in 1..20) {
                        val restored = coldCache.cache(redisArtistSongsCache(artistId, songId)) {
                            coldLoads++
                            Bar(songId, "miss $artistId-$songId")
                        }

                        assertEquals(Bar(songId, "Artist $artistId Song $songId"), restored)
                    }
                }

                assertEquals(0, coldLoads)
                assertEquals("hash", redis.commands.type(redisArtistCacheKey(25)))
                assertEquals(20, redis.commands.hlen(redisArtistCacheKey(25)))
            } finally {
                coldScope.cancel()
            }
        } finally {
            redis.close()
        }
    }
}

private fun redisSnapshotConfig(
    restore: SnapshotRestore,
    flushInterval: Duration,
): CacheConfig = CacheConfig(
    name = "artist-cache",
    snapshot = persistentSnapshot(
        restore = restore,
        flushInterval = flushInterval,
        chunkHashLength = 1,
    ),
)

private suspend fun eventually(condition: suspend () -> Boolean) {
    withContext(Dispatchers.Default) {
        withTimeout(30_000) {
            while (!condition()) {
                delay(25)
            }
        }
    }
}
