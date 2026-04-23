@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cache1
import com.github.dave08.kacheable.cache3
import com.github.dave08.kacheable.CacheStorageLayout
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.CacheWildcard
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.patternArgs
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class Song(val id: Int, val title: String)

@Serializable
private data class SearchSongsKey(
    val query: String,
    val page: Int,
    val limit: Int,
)

private val structuredKeyStrategy = GetNameStrategy { name, params ->
    when (name) {
        "song-page-cache" -> "$name|song=${params[0]}|page=${params[1]}|limit=${params[2]}"
        else -> if (params.isEmpty()) name else "$name:${params.joinToString(",")}"
    }
}

val TypedExactApiSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("cache1 caches exact values with the suspending engine") {
            val songCache = cache1<Int, Song>("song-cache")
            var calls = 0

            val first = cache(songCache(7)) {
                calls++
                Song(7, "Track")
            }
            val second = cache(songCache(7)) {
                calls++
                Song(7, "Other")
            }

            assertEquals(Song(7, "Track"), first)
            assertEquals(Song(7, "Track"), second)
            assertEquals(1, calls)
            assertEquals("""{"id":7,"title":"Track"}""", store.get("song-cache:7"))
        }

        test("typed exact invalidation removes one cached value") {
            val songCache = cache1<Int, Song>("song-cache")

            cache(songCache(3)) { Song(3, "Melody") }
            cache.invalidate(songCache(3))

            assertNull(store.get("song-cache:3"))
        }

        test("key-object fallback supports more complex exact keys") {
            val searchCache = cache<SearchSongsKey, List<Int>>("search-songs-cache")

            val result = cache(searchCache(SearchSongsKey("query", 2, 20))) {
                listOf(1, 2, 3)
            }

            assertEquals(listOf(1, 2, 3), result)
            assertEquals("""[1,2,3]""", store.get("search-songs-cache:SearchSongsKey(query=query, page=2, limit=20)"))
        }

        test("cacheIf can skip saving a typed cache result") {
            val songCache = cache1<Int, Song>("song-cache")

            val result = cache(songCache(9), cacheIf = { false }) {
                Song(9, "Live")
            }

            assertEquals(Song(9, "Live"), result)
            assertNull(store.get("song-cache:9"))
        }
    }

    testFixture {
        SuspendCacheFixture(getNameStrategy = structuredKeyStrategy)
    } asContextForEach {
        test("typed calls respect a custom name strategy for structured keys") {
            val pageCache = cache3<Int, Int, Int, Song>(
                name = "song-page-cache",
                storageLayout = CacheStorageLayout.HashValue,
            )

            val result = cache(pageCache(7, 2, 25)) {
                Song(7, "Page A")
            }

            assertEquals(Song(7, "Page A"), result)
            assertEquals(CacheStorageLayout.HashValue, pageCache.storageLayout)
            assertEquals(
                """{"id":7,"title":"Page A"}""",
                store.get("song-page-cache|song=7|page=2|limit=25"),
            )
        }

        test("group invalidation can remove all matching structured pages") {
            val pageCache = cache3<Int, Int, Int, Song, Int>(
                name = "song-page-cache",
                storageLayout = CacheStorageLayout.HashValue,
                groupArgs = { songId -> patternArgs(songId, CacheWildcard, CacheWildcard) },
            )

            cache(pageCache(7, 1, 25)) { Song(7, "Page 1") }
            cache(pageCache(7, 2, 25)) { Song(7, "Page 2") }
            cache(pageCache(8, 1, 25)) { Song(8, "Other Song") }

            cache.invalidate(pageCache.group(7))

            assertNull(store.get("song-page-cache|song=7|page=1|limit=25"))
            assertNull(store.get("song-page-cache|song=7|page=2|limit=25"))
            assertEquals("""{"id":8,"title":"Other Song"}""", store.get("song-page-cache|song=8|page=1|limit=25"))
        }
    }

    testFixture {
        BlockingCacheFixture()
    } asContextForEach {
        test("the same typed definition works with the blocking engine") {
            val songCache = cache1<Int, Song>("song-cache")
            var calls = 0

            val first = cache(songCache(11)) {
                calls++
                Song(11, "Blocking")
            }
            val second = cache(songCache(11)) {
                calls++
                Song(11, "Other")
            }

            assertEquals(Song(11, "Blocking"), first)
            assertEquals(Song(11, "Blocking"), second)
            assertEquals(1, calls)
            assertEquals("""{"id":11,"title":"Blocking"}""", store.get("song-cache:11"))
        }
    }

    testFixture {
        BlockingCacheFixture(getNameStrategy = structuredKeyStrategy)
    } asContextForEach {
        test("blocking typed calls respect a custom name strategy for structured keys") {
            val pageCache = cache3<Int, Int, Int, Song>(
                name = "song-page-cache",
                storageLayout = CacheStorageLayout.HashValue,
            )

            val result = cache(pageCache(11, 3, 50)) {
                Song(11, "Blocking Page")
            }

            assertEquals(Song(11, "Blocking Page"), result)
            assertEquals(CacheStorageLayout.HashValue, pageCache.storageLayout)
            assertEquals(
                """{"id":11,"title":"Blocking Page"}""",
                store.get("song-page-cache|song=11|page=3|limit=50"),
            )
        }

        test("blocking group invalidation can remove all matching structured pages") {
            val pageCache = cache3<Int, Int, Int, Song, Int>(
                name = "song-page-cache",
                storageLayout = CacheStorageLayout.HashValue,
                groupArgs = { songId -> patternArgs(songId, CacheWildcard, CacheWildcard) },
            )

            cache(pageCache(11, 1, 50)) { Song(11, "Blocking Page 1") }
            cache(pageCache(11, 2, 50)) { Song(11, "Blocking Page 2") }
            cache(pageCache(12, 1, 50)) { Song(12, "Blocking Other") }

            cache.invalidate(pageCache.group(11))

            assertNull(store.get("song-page-cache|song=11|page=1|limit=50"))
            assertNull(store.get("song-page-cache|song=11|page=2|limit=50"))
            assertEquals("""{"id":12,"title":"Blocking Other"}""", store.get("song-page-cache|song=12|page=1|limit=50"))
        }
    }
}
