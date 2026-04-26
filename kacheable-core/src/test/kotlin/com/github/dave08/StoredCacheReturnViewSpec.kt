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
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class CachedSong(val id: Int, val title: String)

private val artistCache = mainKey<Int>("artist-cache", storedAs = CacheStorage.HashMap)
private val songKey = key<Int>()
private val artistSongsCache = artistCache + songKey

private fun artistCacheKey(artistId: Int): String = "artist-cache:$artistId"

private fun artistSongFlatKey(artistId: Int, songId: Int): String = "artist-cache:$artistId,$songId"

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
            assertEquals(listOf(artistId), artistSongsCache.key(artistId, songId).keyGroups.main.toParamsArray().toList())
            assertEquals(listOf(songId), artistSongsCache.key(artistId, songId).keyGroups.secondary?.toParamsArray()?.toList())
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
            assertEquals(listOf(artistId), artistSongsCache.key(artistId).keyGroups.main.toParamsArray().toList())
            assertNull(artistSongsCache.key(artistId).keyGroups.secondary)
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

            cache.invalidate(artistSongsCache.keyPart(artistCache.keyPart(artistId)))

            store.assertHashMissing(artistCacheKey(artistId))
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
