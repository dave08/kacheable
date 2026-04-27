@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.InMemoryKacheableStore
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.key
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.map
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.rawStringCacheValueCodec
import com.github.dave08.kacheable.value
import com.github.dave08.kacheable.blocking.invoke
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class CachedSong(val id: Int, val title: String)

private data class ResultPage(val offset: Int, val limit: Int)

private val artistCache = mainKey<Int>("artist-cache", storedAs = CacheStorage.HashMap)
private val songKey = key<Int>()
private val pageKey = key<ResultPage>(ResultPage::offset, ResultPage::limit)
private val filterKey = key<String>()
private val sortKey = key<String>()
private val pageSizeKey = key<Int>()
private val marketKey = key<String>()
private val localeKey = key<String>()
private val artistSongsCache = artistCache + songKey
private val artistPageCache = artistCache + (pageKey + localeKey)
private val artistCatalogCache = artistCache + (filterKey + sortKey + pageSizeKey + marketKey + localeKey)

private fun artistCacheKey(artistId: Int): String = "artist-cache:$artistId"

private fun artistSongFlatKey(artistId: Int, songId: Int): String = "artist-cache:$artistId,$songId"

private fun artistCatalogField(
    filter: String,
    sort: String,
    pageSize: Int,
    market: String,
    locale: String,
): String = "$filter,$sort,$pageSize,$market,$locale"

val StoredCacheReturnViewSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("cache can use value return descriptors for hash fields") {
            val artistId = 3
            val songId = 7
            var calls = 0

            val first = cache(artistSongsCache.key(artistId, songId), returnsAs = value<CachedSong>()) {
                calls++
                CachedSong(songId, "Seven")
            }
            val second = cache(artistSongsCache.key(artistId, songId), returnsAs = value<CachedSong>()) {
                calls++
                CachedSong(songId, "Changed")
            }

            assertEquals(first, second)
            assertEquals(1, calls)
            store.assertHashField(artistCacheKey(artistId), songId, """{"id":7,"title":"Seven"}""")
            store.assertStringValueMissing(artistSongFlatKey(artistId, songId))
        }

        test("cache can use map return descriptors for whole hash maps") {
            val artistId = 3
            val songId = 7
            val changedSongId = 8
            var calls = 0

            val first = cache(artistSongsCache.key(artistId), returnsAs = map<Int, CachedSong>()) {
                calls++
                mapOf(songId to CachedSong(songId, "Seven"))
            }
            val second = cache(artistSongsCache.key(artistId), returnsAs = map<Int, CachedSong>()) {
                calls++
                mapOf(changedSongId to CachedSong(changedSongId, "Changed"))
            }

            assertEquals(first, second)
            assertEquals(1, calls)
            store.assertStringValue(artistCacheKey(artistId), """{"7":{"id":7,"title":"Seven"}}""")
            store.assertHashMissing(artistCacheKey(artistId))
        }

        test("stored cache key parts can invalidate entries cached with return descriptors") {
            val artistId = 3
            val firstSongId = 7
            val secondSongId = 8

            cache(artistSongsCache.key(artistId, firstSongId), returnsAs = value<CachedSong>()) {
                CachedSong(firstSongId, "Seven")
            }
            cache(artistSongsCache.key(artistId, secondSongId), returnsAs = value<CachedSong>()) {
                CachedSong(secondSongId, "Eight")
            }

            cache.invalidate(artistSongsCache.keyPart(artistId))

            store.assertHashMissing(artistCacheKey(artistId))
        }

        test("hash map stored caches support composed secondary key parts") {
            val artistId = 3
            val page = ResultPage(offset = 20, limit = 10)
            val locale = "en"

            val result = cache(artistPageCache.key(artistId, page, locale), returnsAs = value<List<CachedSong>>()) {
                listOf(CachedSong(7, "Seven"))
            }

            assertEquals(listOf(CachedSong(7, "Seven")), result)
            store.assertHashField(
                artistCacheKey(artistId),
                "20,10,en",
                """[{"id":7,"title":"Seven"}]""",
            )
        }

        test("value return descriptors can store set values") {
            val artistId = 3
            val page = ResultPage(offset = 20, limit = 10)
            val locale = "en"

            val result = cache(artistPageCache.key(artistId, page, locale), returnsAs = value<Set<Int>>()) {
                setOf(7, 8, 9)
            }

            assertEquals(setOf(7, 8, 9), result)
            store.assertHashField(artistCacheKey(artistId), "20,10,en", """[7,8,9]""")
        }

        test("value return descriptors can use custom value codecs") {
            val artistId = 3
            val songId = 7

            val result = cache(
                artistSongsCache.key(artistId, songId),
                returnsAs = value(codec = rawStringCacheValueCodec()),
            ) {
                "Plain Title"
            }

            assertEquals("Plain Title", result)
            store.assertHashField(artistCacheKey(artistId), songId, "Plain Title")
        }

        test("hash map stored caches support the maximum cache arity") {
            val artistId = 3
            val filter = "favorites"
            val sort = "recent"
            val pageSize = 25
            val market = "us"
            val locale = "en"
            val songId = 7

            val result = cache(
                artistCatalogCache.key(artistId, filter, sort, pageSize, market, locale),
                returnsAs = value<CachedSong>(),
            ) {
                CachedSong(songId, "Seven")
            }

            assertEquals(CachedSong(songId, "Seven"), result)
            store.assertHashField(
                artistCacheKey(artistId),
                artistCatalogField(filter, sort, pageSize, market, locale),
                """{"id":7,"title":"Seven"}""",
            )
        }
    }

    testFixture {
        BlockingCacheFixture()
    } asContextForEach {
        test("blocking cache can use storage-compatible return descriptors") {
            val artistId = 3
            val songId = 7
            var calls = 0

            val first = cache(artistSongsCache.key(artistId, songId), returnsAs = value<CachedSong>()) {
                calls++
                CachedSong(songId, "Seven")
            }
            val second = cache(artistSongsCache.key(artistId, songId), returnsAs = value<CachedSong>()) {
                calls++
                CachedSong(songId, "Changed")
            }

            assertEquals(first, second)
            assertEquals(1, calls)
            store.assertHashField(artistCacheKey(artistId), songId, """{"id":7,"title":"Seven"}""")
        }
    }
}

private fun InMemoryKacheableStore.assertHashField(
    key: String,
    field: Any,
    expectedValue: String,
) {
    assertEquals(expectedValue, hashMap[key]?.get(field.toString()))
}

private fun InMemoryKacheableStore.assertHashMissing(key: String) {
    assertNull(hashMap[key])
}

private suspend fun InMemoryKacheableStore.assertStringValue(
    key: String,
    expectedValue: String,
) {
    assertEquals(expectedValue, get(key))
}

private suspend fun InMemoryKacheableStore.assertStringValueMissing(key: String) {
    assertNull(get(key))
}

private fun InMemoryBlockingKacheableStore.assertHashField(
    key: String,
    field: Any,
    expectedValue: String,
) {
    assertEquals(expectedValue, hashMap[key]?.get(field.toString()))
}
