@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.defaultCacheNamingStrategy
import com.github.dave08.kacheable.enumMember
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.isMember
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull

val CacheNamingStrategyCompatibilitySpec by testSuite {
    testFixture {
        SuspendCacheFixture(namingStrategy = bracketedKeyStrategy)
    } asContextForEach {
        test("raw cache calls use the configured key naming strategy") {
            val result = cache<TestSong>("song-cache", 7, "en") {
                TestSong(7, "Track")
            }

            assertEquals(TestSong(7, "Track"), result)
            assertEquals("""{"id":7,"title":"Track"}""", store.get("song-cache[7][en]"))
        }

        test("typed hash caches keep grouped invalidation compatible with a custom name strategy") {
            val artistId = 7

            cache(typedSongPageCache.key(artistId, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page 1")
            }
            cache(typedSongPageCache.key(artistId, PageWindow(10, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page 2")
            }

            cache.invalidate(typedSongPageCache.keyPart(artistId))

            assertNull(store.hashMap["song-page-cache[7]"])
        }

        test("set membership uses the configured primary key naming and preserves the internal suffix") {
            val artistId = 3
            val accountId = 7

            cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                false
            }

            store.assertSetMember("artist-followers-cache[3]:__kacheable_non_members", accountId)
            store.assertSetMissing("artist-followers-cache[3]")
        }

        test("classified membership uses the configured primary key naming for each value set") {
            val songId = 9
            val accountId = 11

            cache(songLikeCache.key(songId, accountId), returnsAs = enumMember<SongLike>()) {
                SongLike.DISLIKE
            }

            store.assertSetMember("song-like-cache[9]:DISLIKE", accountId)
            store.assertSetMissing("song-like-cache[9]:LIKE")
            store.assertSetMissing("song-like-cache[9]:NONE")
        }

        test("hash storage uses the naming strategy for secondary entry naming too") {
            val artistId = 7

            cache(typedSongPageCache.key(artistId, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Track")
            }

            assertEquals(
                """{"id":7,"title":"Track"}""",
                store.getHashValue("song-page-cache[7]", "0,10,en"),
            )
        }
    }

    test("deprecated GetNameStrategy factory still routes raw cache naming through the new strategy") {
        val store = com.github.dave08.kacheable.store.InMemoryKacheableStore()
        val cache = Kacheable(
            store = store,
            getNameStrategy = GetNameStrategy { name, params ->
                if (params.isEmpty()) name else "$name<${params.joinToString("|")}>"
            },
        )

        val result = cache<TestSong>("song-cache", 7, "en") {
            TestSong(7, "Track")
        }

        assertEquals(TestSong(7, "Track"), result)
        assertEquals("""{"id":7,"title":"Track"}""", store.get("song-cache<7|en>"))
    }

    test("custom naming strategies can define secondary entry formatting for hash storage") {
        val fixture = SuspendCacheFixture(namingStrategy = verboseEntryStrategy)

        with(fixture) {
            cache(typedSongPageCache.key(7, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(7, "Track")
            }

            assertEquals(
                """{"id":7,"title":"Track"}""",
                store.getHashValue("song-page-cache|7", "part0=0|part1=10|part2=en"),
            )
        }
    }

    test("default cache naming strategy can customize only the layered entry combiner") {
        val fixture = SuspendCacheFixture(
            namingStrategy = defaultCacheNamingStrategy(
                secondaryEntryCombiner = { params -> params.joinToString("|") },
            ),
        )

        with(fixture) {
            cache(typedSongPageCache.key(7, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(7, "Track")
            }

            assertEquals(
                """{"id":7,"title":"Track"}""",
                store.getHashValue("song-page-cache:7", "0|10|en"),
            )
        }
    }
}
