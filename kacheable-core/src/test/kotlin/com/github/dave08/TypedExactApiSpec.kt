@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheStorageLayout
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.argsOf
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cache1
import com.github.dave08.kacheable.cacheArgsEncoder
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.key
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.rawStringCacheValueCodec
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
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

private data class SongId(val value: Int)

private data class SongSection(
    val id: SongId,
    val category: String,
)

private val structuredKeyStrategy = GetNameStrategy { name, params ->
    when (name) {
        "song-page-cache" -> "$name|song=${params[0]}|page=${params[1]}|limit=${params[2]}|locale=${params[3]}"
        "song-section-cache" -> "$name:${params.joinToString("|")}"
        "song-window-cache" -> "$name|song=${params[0]}|page=${params[1]}|limit=${params[2]}"
        "wide-cache" -> "$name:${params.joinToString("|")}"
        else -> if (params.isEmpty()) name else "$name:${params.joinToString(",")}"
    }
}

private val pageWindowArgs = cacheArgsEncoder<PageWindow> { window ->
    argsOf(window.offset, window.limit)
}

private val songIdMapper = key<SongId>(SongId::value)
private val songSectionMapper = key<SongSection>({ it.id.value }, SongSection::category)
private val songKey = mainKey<Int>("song")
private val songIdKey = mainKey("song", songIdMapper)
private val pagingKey = key<PageWindow>(PageWindow::offset, PageWindow::limit)
private val localeKey = key<String>()
private val filterKey = key<String>()
private val sortKey = key<String>()
private val pageSizeKey = key<Int>()
private val marketKey = key<String>()

private val songPageKey = songKey + (pagingKey + localeKey)
private val songSectionKey = songIdKey + songSectionMapper
private val wideSongKey = songKey + (filterKey + sortKey + pageSizeKey + marketKey + localeKey)

private fun CacheArgs.toList(): List<Any> = toParamsArray().toList()

val TypedExactApiSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("cache1 caches exact values with the suspending engine") {
            val songByIdCache = cache1<Int, Song>("song-cache")
            var entryRefs = 0

            val first = cache(songByIdCache.key(7)) {
                entryRefs++
                Song(7, "Track")
            }
            val second = cache(songByIdCache.key(7)) {
                entryRefs++
                Song(7, "Other")
            }

            assertEquals(Song(7, "Track"), first)
            assertEquals(Song(7, "Track"), second)
            assertEquals(1, entryRefs)
            assertEquals("""{"id":7,"title":"Track"}""", store.get("song-cache:7"))
        }

        test("typed exact invalidation removes one cached value") {
            val songByIdCache = cache1<Int, Song>("song-cache")

            cache(songByIdCache.key(3)) { Song(3, "Melody") }
            cache.invalidate(songByIdCache.key(3))

            assertNull(store.get("song-cache:3"))
        }

        test("key-object fallback supports more complex exact keys") {
            val searchCache = cache<SearchSongsKey, List<Int>>("search-songs-cache")

            val result = cache(searchCache.key(SearchSongsKey("query", 2, 20))) {
                listOf(1, 2, 3)
            }

            assertEquals(listOf(1, 2, 3), result)
            assertEquals("""[1,2,3]""", store.get("search-songs-cache:SearchSongsKey(query=query, page=2, limit=20)"))
        }

        test("structured cache definitions can return lists") {
            val songsByPageCache = cache("songs-by-page-cache", songPageKey, serializer<List<Song>>())

            val result = cache(songsByPageCache.key(7, PageWindow(0, 10), "en")) {
                listOf(Song(1, "One"), Song(2, "Two"))
            }

            assertEquals(listOf(Song(1, "One"), Song(2, "Two")), result)
            assertEquals(
                """[{"id":1,"title":"One"},{"id":2,"title":"Two"}]""",
                store.get("songs-by-page-cache:7,0,10,en"),
            )
        }

        test("structured cache definitions can return sets") {
            val songIdsByPageCache = cache("song-ids-by-page-cache", songPageKey, serializer<Set<Int>>())

            val result = cache(songIdsByPageCache.key(7, PageWindow(0, 10), "en")) {
                setOf(1, 2, 3)
            }

            assertEquals(setOf(1, 2, 3), result)
            assertEquals("""[1,2,3]""", store.get("song-ids-by-page-cache:7,0,10,en"))
        }

        test("structured cache definitions can return maps") {
            val songsBySlugCache = cache("songs-by-slug-cache", songPageKey, serializer<Map<String, Song>>())

            val result = cache(songsBySlugCache.key(7, PageWindow(0, 10), "en")) {
                mapOf("one" to Song(1, "One"), "two" to Song(2, "Two"))
            }

            assertEquals(mapOf("one" to Song(1, "One"), "two" to Song(2, "Two")), result)
            assertEquals(
                """{"one":{"id":1,"title":"One"},"two":{"id":2,"title":"Two"}}""",
                store.get("songs-by-slug-cache:7,0,10,en"),
            )
        }

        test("reusable args encoders can still be shared across keyed cache definitions") {
            val songWindowCache = cache<PageWindow, Song>(
                name = "song-window-cache",
                argsEncoder = pageWindowArgs,
            )

            cache(songWindowCache.key(PageWindow(0, 10))) {
                Song(7, "Windowed")
            }

            assertEquals("""{"id":7,"title":"Windowed"}""", store.get("song-window-cache:0,10"))
        }

        test("main and secondary key parts support grouped invalidation") {
            val songPageCache = cache("song-page-cache", songPageKey, serializer<Song>())

            cache(songPageCache.key(7, PageWindow(0, 10), "en")) {
                Song(7, "Page 1")
            }
            cache(songPageCache.key(7, PageWindow(10, 10), "en")) {
                Song(7, "Page 2")
            }
            cache(songPageCache.key(8, PageWindow(0, 10), "en")) {
                Song(8, "Other Song")
            }

            cache.invalidate(songPageCache.keyPart(songKey(7)))

            assertNull(store.get("song-page-cache:7,0,10,en"))
            assertNull(store.get("song-page-cache:7,10,10,en"))
            assertEquals("""{"id":8,"title":"Other Song"}""", store.get("song-page-cache:8,0,10,en"))
        }

        test("entry refs expose logical key parts") {
            val songPageCache = cache(
                name = "song-page-cache",
                key = songPageKey,
                serializer = serializer<Song>(),
                storageLayout = CacheStorageLayout.HashValue,
            )

            val entryRef = songPageCache.key(7, PageWindow(0, 10), "en")
            val partRef = songPageCache.keyPart(songKey(7))

            assertEquals(CacheStorageLayout.HashValue, entryRef.definition.storageLayout)
            assertEquals(listOf(7, 0, 10, "en"), entryRef.args.toList())
            assertEquals(listOf(7), entryRef.keyGroups.main.toList())
            assertEquals(listOf(0, 10, "en"), entryRef.keyGroups.secondary?.toList())
            assertEquals(listOf(7, 0, 10, "en"), entryRef.keyGroups.flattened.toList())
            assertEquals(listOf("7", "*", "*", "*"), partRef.args.toList().map(Any::toString))
            assertEquals(listOf(7), partRef.keyGroups.main.toList())
            assertEquals(listOf("*", "*", "*"), partRef.keyGroups.secondary?.toList()?.map(Any::toString))
            assertEquals(listOf("7", "*", "*", "*"), partRef.keyGroups.flattened.toList().map(Any::toString))
        }

        test("main keys can use explicit mappers for value classes") {
            val songByIdCache = cache("song-cache", songIdKey, serializer<Song>())

            cache(songByIdCache.key(SongId(13))) {
                Song(13, "Mapped Id")
            }

            cache.invalidate(songByIdCache.keyPart(songIdKey(SongId(13))))

            assertNull(store.get("song-cache:13"))
        }

        test("secondary mappers can encode multiple fields from one parameter") {
            val sectionCache = cache("song-section-cache", songSectionKey, serializer<Song>())

            cache(sectionCache.key(SongId(4), SongSection(SongId(4), "lyrics"))) {
                Song(4, "Section A")
            }
            cache(sectionCache.key(SongId(4), SongSection(SongId(4), "credits"))) {
                Song(4, "Section B")
            }
            cache(sectionCache.key(SongId(5), SongSection(SongId(5), "lyrics"))) {
                Song(5, "Other Section")
            }

            cache.invalidate(sectionCache.keyPart(songIdKey(SongId(4))))

            assertNull(store.get("song-section-cache:4,4,lyrics"))
            assertNull(store.get("song-section-cache:4,4,credits"))
            assertEquals("""{"id":5,"title":"Other Section"}""", store.get("song-section-cache:5,5,lyrics"))
        }

        test("cacheIf can skip saving a typed cache result") {
            val songByIdCache = cache1<Int, Song>("song-cache")

            val result = cache(songByIdCache.key(9), cacheIf = { false }) {
                Song(9, "Live")
            }

            assertEquals(Song(9, "Live"), result)
            assertNull(store.get("song-cache:9"))
        }

        test("value codecs can store raw strings without json quoting") {
            val rawTitleKey = mainKey<String>("title")
            val rawTitleCache = cache(
                name = "raw-title-cache",
                key = rawTitleKey,
                serializer = serializer<String>(),
                codec = rawStringCacheValueCodec(),
            )

            val result = cache(rawTitleCache.key("title")) {
                "Plain Title"
            }

            assertEquals("Plain Title", result)
            assertEquals("Plain Title", store.get("raw-title-cache:title"))
        }
    }

    testFixture {
        SuspendCacheFixture(getNameStrategy = structuredKeyStrategy)
    } asContextForEach {
        test("structured key parts respect a custom name strategy") {
            val songPageCache = cache("song-page-cache", songPageKey, serializer<Song>())

            val result = cache(songPageCache.key(7, PageWindow(2, 25), "en")) {
                Song(7, "Page A")
            }

            assertEquals(Song(7, "Page A"), result)
            assertEquals(
                """{"id":7,"title":"Page A"}""",
                store.get("song-page-cache|song=7|page=2|limit=25|locale=en"),
            )
        }

        test("secondary composition can cover five function parameters under one main key") {
            val wideCache = cache("wide-cache", wideSongKey, serializer<Song>())

            cache(wideCache.key(7, "favorites", "recent", 25, "us", "en")) {
                Song(7, "Wide 1")
            }
            cache(wideCache.key(7, "favorites", "popular", 25, "us", "en")) {
                Song(7, "Wide 2")
            }
            cache(wideCache.key(9, "favorites", "recent", 25, "us", "en")) {
                Song(9, "Wide Other")
            }

            cache.invalidate(wideCache.keyPart(songKey(7)))

            assertNull(store.get("wide-cache:7|favorites|recent|25|us|en"))
            assertNull(store.get("wide-cache:7|favorites|popular|25|us|en"))
            assertEquals("""{"id":9,"title":"Wide Other"}""", store.get("wide-cache:9|favorites|recent|25|us|en"))
        }
    }

    testFixture {
        BlockingCacheFixture()
    } asContextForEach {
        test("the same typed definition works with the blocking engine") {
            val songByIdCache = cache1<Int, Song>("song-cache")
            var entryRefs = 0

            val first = cache(songByIdCache.key(11)) {
                entryRefs++
                Song(11, "Blocking")
            }
            val second = cache(songByIdCache.key(11)) {
                entryRefs++
                Song(11, "Other")
            }

            assertEquals(Song(11, "Blocking"), first)
            assertEquals(Song(11, "Blocking"), second)
            assertEquals(1, entryRefs)
            assertEquals("""{"id":11,"title":"Blocking"}""", store.get("song-cache:11"))
        }
    }

    testFixture {
        BlockingCacheFixture(getNameStrategy = structuredKeyStrategy)
    } asContextForEach {
        test("blocking typed grouped invalidation uses the same definition") {
            val songPageCache = cache("song-page-cache", songPageKey, serializer<Song>())

            cache(songPageCache.key(11, PageWindow(3, 50), "en")) {
                Song(11, "Blocking Page")
            }
            cache(songPageCache.key(11, PageWindow(4, 50), "en")) {
                Song(11, "Blocking Page 2")
            }
            cache(songPageCache.key(12, PageWindow(3, 50), "en")) {
                Song(12, "Blocking Other")
            }

            cache.invalidate(songPageCache.keyPart(songKey(11)))

            assertNull(store.get("song-page-cache|song=11|page=3|limit=50|locale=en"))
            assertNull(store.get("song-page-cache|song=11|page=4|limit=50|locale=en"))
            assertEquals("""{"id":12,"title":"Blocking Other"}""", store.get("song-page-cache|song=12|page=3|limit=50|locale=en"))
        }
    }
}
