@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorageLayout
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.key
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.plus
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class CachedSong(val id: Int, val title: String)

private val artistKey = mainKey<Int>("artist")
private val songKey = key<Int>()
private val artistSongKey = artistKey + songKey

val CacheSpaceReturnViewSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("withReturnValue creates a typed cache view from a cache space") {
            val songs = cache("songs-cache", artistSongKey)
            val songValues = songs.withReturnValue<CachedSong>(
                storageLayout = CacheStorageLayout.HashValue,
            )
            var calls = 0

            val first = cache(songValues.key(3, 7)) {
                calls++
                CachedSong(7, "Seven")
            }
            val second = cache(songValues.key(3, 7)) {
                calls++
                CachedSong(7, "Changed")
            }

            assertEquals(first, second)
            assertEquals(1, calls)
            assertEquals("songs-cache", songValues.name)
            assertEquals(CacheStorageLayout.HashValue, songValues.storageLayout)
            assertEquals(listOf(3), songValues.key(3, 7).keyGroups.main.toParamsArray().toList())
            assertEquals(listOf(7), songValues.key(3, 7).keyGroups.secondary?.toParamsArray()?.toList())
            assertEquals("""{"id":7,"title":"Seven"}""", store.hashMap["songs-cache:3"]?.get("7"))
            assertNull(store.get("songs-cache:3,7"))
        }

        test("withReturnValue can use an explicit serializer overload") {
            val songs = cache("songs-cache", artistSongKey)
            val songValues = songs.withReturnValue(
                serializer = serializer<CachedSong>(),
                storageLayout = CacheStorageLayout.HashValue,
            )

            cache(songValues.key(3, 7)) {
                CachedSong(7, "Explicit")
            }

            assertEquals("""{"id":7,"title":"Explicit"}""", store.hashMap["songs-cache:3"]?.get("7"))
        }

        test("withReturnMap creates a whole-map view under the main cache key") {
            val songs = cache("songs-cache", artistSongKey)
            val songMap = songs.withReturnMap<Int, CachedSong>(
                storageLayout = CacheStorageLayout.HashValue,
            )
            var calls = 0

            val first = cache(songMap.key(3)) {
                calls++
                mapOf(7 to CachedSong(7, "Seven"))
            }
            val second = cache(songMap.key(3)) {
                calls++
                mapOf(8 to CachedSong(8, "Changed"))
            }

            assertEquals(first, second)
            assertEquals(1, calls)
            assertEquals(CacheStorageLayout.HashValue, songMap.storageLayout)
            assertEquals(listOf(3), songMap.key(3).keyGroups.main.toParamsArray().toList())
            assertNull(songMap.key(3).keyGroups.secondary)
            assertEquals("""{"7":{"id":7,"title":"Seven"}}""", store.get("songs-cache:3"))
            assertNull(store.hashMap["songs-cache:3"])
        }

        test("cache space key parts can invalidate return value views") {
            val songs = cache("songs-cache", artistSongKey)
            val songValues = songs.withReturnValue<CachedSong>(
                storageLayout = CacheStorageLayout.HashValue,
            )

            cache(songValues.key(3, 7)) {
                CachedSong(7, "Seven")
            }
            cache(songValues.key(3, 8)) {
                CachedSong(8, "Eight")
            }

            cache.invalidate(songValues.keyPart(artistKey(3)))

            assertNull(store.hashMap["songs-cache:3"])
        }
    }
}
