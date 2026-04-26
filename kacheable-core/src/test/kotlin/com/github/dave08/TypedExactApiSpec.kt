@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.argsOf
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cache1
import com.github.dave08.kacheable.cache3
import com.github.dave08.kacheable.cache6
import com.github.dave08.kacheable.cacheArgsEncoder
import com.github.dave08.kacheable.composite
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.mainKeyPart
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.secondaryKeyPart
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

private data class PageWindow(
    val offset: Int,
    val limit: Int,
)

private val structuredKeyStrategy = GetNameStrategy { name, params ->
    when (name) {
        "song-page-cache" -> "$name|song=${params[0]}|page=${params[1]}|limit=${params[2]}|locale=${params[3]}"
        "song-window-cache" -> "$name|song=${params[0]}|page=${params[1]}|limit=${params[2]}"
        "wide-cache" -> "$name:${params.joinToString("|")}"
        else -> if (params.isEmpty()) name else "$name:${params.joinToString(",")}"
    }
}

private val pageWindowArgs = cacheArgsEncoder<PageWindow> { window ->
    argsOf(window.offset, window.limit)
}

private val songPart = mainKeyPart<Int>("song", { it })
private val pagingPart = secondaryKeyPart<PageWindow>(PageWindow::offset, PageWindow::limit)
private val localePart = secondaryKeyPart<String>({ it })
private val filterPart = secondaryKeyPart<String>({ it })
private val sortPart = secondaryKeyPart<String>({ it })
private val pageSizePart = secondaryKeyPart<Int>({ it })
private val marketPart = secondaryKeyPart<String>({ it })

private val songPageKey = songPart + composite(pagingPart + localePart)
private val wideSongKey = songPart + composite(filterPart + sortPart + pageSizePart + marketPart + localePart)

val TypedExactApiSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("cache1 caches exact values with the suspending engine") {
            val typedSongCache = cache1<Int, Song>("song-cache")
            var calls = 0

            val first = cache(typedSongCache(7)) {
                calls++
                Song(7, "Track")
            }
            val second = cache(typedSongCache(7)) {
                calls++
                Song(7, "Other")
            }

            assertEquals(Song(7, "Track"), first)
            assertEquals(Song(7, "Track"), second)
            assertEquals(1, calls)
            assertEquals("""{"id":7,"title":"Track"}""", store.get("song-cache:7"))
        }

        test("typed exact invalidation removes one cached value") {
            val typedSongCache = cache1<Int, Song>("song-cache")

            cache(typedSongCache(3)) { Song(3, "Melody") }
            cache.invalidate(typedSongCache(3))

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

        test("reusable args encoders can still be shared across keyed cache definitions") {
            val songWindowCache = cache<PageWindow, Song>(
                name = "song-window-cache",
                argsEncoder = pageWindowArgs,
            )

            cache(songWindowCache(PageWindow(0, 10))) {
                Song(7, "Windowed")
            }

            assertEquals("""{"id":7,"title":"Windowed"}""", store.get("song-window-cache:0,10"))
        }

        test("main and secondary key parts support grouped invalidation") {
            val songPageCache = cache3<Int, PageWindow, String, Song>(
                name = "song-page-cache",
                key = songPageKey,
            )

            cache(songPageCache(7, PageWindow(0, 10), "en")) {
                Song(7, "Page 1")
            }
            cache(songPageCache(7, PageWindow(10, 10), "en")) {
                Song(7, "Page 2")
            }
            cache(songPageCache(8, PageWindow(0, 10), "en")) {
                Song(8, "Other Song")
            }

            cache.invalidate(songPageCache.group(7))

            assertNull(store.get("song-page-cache:7,0,10,en"))
            assertNull(store.get("song-page-cache:7,10,10,en"))
            assertEquals("""{"id":8,"title":"Other Song"}""", store.get("song-page-cache:8,0,10,en"))
        }

        test("cacheIf can skip saving a typed cache result") {
            val typedSongCache = cache1<Int, Song>("song-cache")

            val result = cache(typedSongCache(9), cacheIf = { false }) {
                Song(9, "Live")
            }

            assertEquals(Song(9, "Live"), result)
            assertNull(store.get("song-cache:9"))
        }
    }

    testFixture {
        SuspendCacheFixture(getNameStrategy = structuredKeyStrategy)
    } asContextForEach {
        test("structured key parts respect a custom name strategy") {
            val songPageCache = cache3<Int, PageWindow, String, Song>(
                name = "song-page-cache",
                key = songPageKey,
            )

            val result = cache(songPageCache(7, PageWindow(2, 25), "en")) {
                Song(7, "Page A")
            }

            assertEquals(Song(7, "Page A"), result)
            assertEquals(
                """{"id":7,"title":"Page A"}""",
                store.get("song-page-cache|song=7|page=2|limit=25|locale=en"),
            )
        }

        test("secondary composition can cover five function parameters under one main key") {
            val wideCache = cache6<Int, String, String, Int, String, String, Song>(
                name = "wide-cache",
                key = wideSongKey,
            )

            cache(wideCache(7, "favorites", "recent", 25, "us", "en")) {
                Song(7, "Wide 1")
            }
            cache(wideCache(7, "favorites", "popular", 25, "us", "en")) {
                Song(7, "Wide 2")
            }
            cache(wideCache(9, "favorites", "recent", 25, "us", "en")) {
                Song(9, "Wide Other")
            }

            cache.invalidate(wideCache.group(7))

            assertNull(store.get("wide-cache:7|favorites|recent|25|us|en"))
            assertNull(store.get("wide-cache:7|favorites|popular|25|us|en"))
            assertEquals("""{"id":9,"title":"Wide Other"}""", store.get("wide-cache:9|favorites|recent|25|us|en"))
        }
    }

    testFixture {
        BlockingCacheFixture()
    } asContextForEach {
        test("the same typed definition works with the blocking engine") {
            val typedSongCache = cache1<Int, Song>("song-cache")
            var calls = 0

            val first = cache(typedSongCache(11)) {
                calls++
                Song(11, "Blocking")
            }
            val second = cache(typedSongCache(11)) {
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
        test("blocking typed grouped invalidation uses the same definition") {
            val songPageCache = cache3<Int, PageWindow, String, Song>(
                name = "song-page-cache",
                key = songPageKey,
            )

            cache(songPageCache(11, PageWindow(3, 50), "en")) {
                Song(11, "Blocking Page")
            }
            cache(songPageCache(11, PageWindow(4, 50), "en")) {
                Song(11, "Blocking Page 2")
            }
            cache(songPageCache(12, PageWindow(3, 50), "en")) {
                Song(12, "Blocking Other")
            }

            cache.invalidate(songPageCache.group(11))

            assertNull(store.get("song-page-cache|song=11|page=3|limit=50|locale=en"))
            assertNull(store.get("song-page-cache|song=11|page=4|limit=50|locale=en"))
            assertEquals("""{"id":12,"title":"Blocking Other"}""", store.get("song-page-cache|song=12|page=3|limit=50|locale=en"))
        }
    }
}
