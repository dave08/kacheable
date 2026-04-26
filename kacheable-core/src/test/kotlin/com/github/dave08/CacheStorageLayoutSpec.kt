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
private data class StoredSong(val id: Int, val title: String)

private data class Page(val offset: Int, val limit: Int)

private val artistKey = mainKey<Int>("artist")
private val pageKey = key<Page>(Page::offset, Page::limit)
private val songKey = key<Int>()
private val artistPageKey = artistKey + pageKey
private val artistSongKey = artistKey + songKey

val CacheStorageLayoutSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("string value layout stores values at the flattened key") {
            val songs = cache("songs-by-page-cache", artistPageKey, serializer<List<StoredSong>>())

            cache(songs.key(3, Page(0, 10))) {
                listOf(StoredSong(1, "First"))
            }

            assertEquals("""[{"id":1,"title":"First"}]""", store.get("songs-by-page-cache:3,0,10"))
            assertNull(store.hashMap["songs-by-page-cache:3"]?.get("0,10"))
        }

        test("hash value layout stores secondary parts as hash fields") {
            val songs = cache(
                name = "songs-by-page-cache",
                key = artistPageKey,
                serializer = serializer<List<StoredSong>>(),
                storageLayout = CacheStorageLayout.HashValue,
            )
            var calls = 0

            val first = cache(songs.key(3, Page(0, 10))) {
                calls++
                listOf(StoredSong(1, "First"))
            }
            val second = cache(songs.key(3, Page(0, 10))) {
                calls++
                listOf(StoredSong(2, "Second"))
            }

            assertEquals(first, second)
            assertEquals(1, calls)
            assertNull(store.get("songs-by-page-cache:3,0,10"))
            assertEquals("""[{"id":1,"title":"First"}]""", store.hashMap["songs-by-page-cache:3"]?.get("0,10"))
        }

        test("hash value layout can invalidate one field without deleting sibling fields") {
            val songs = cache(
                name = "songs-by-page-cache",
                key = artistPageKey,
                serializer = serializer<List<StoredSong>>(),
                storageLayout = CacheStorageLayout.HashValue,
            )

            cache(songs.key(3, Page(0, 10))) {
                listOf(StoredSong(1, "First"))
            }
            cache(songs.key(3, Page(10, 10))) {
                listOf(StoredSong(2, "Second"))
            }

            cache.invalidate(songs.key(3, Page(0, 10)))

            assertNull(store.hashMap["songs-by-page-cache:3"]?.get("0,10"))
            assertEquals("""[{"id":2,"title":"Second"}]""", store.hashMap["songs-by-page-cache:3"]?.get("10,10"))
        }

        test("hash value layout can invalidate all fields under the main key") {
            val songs = cache(
                name = "songs-by-artist-cache",
                key = artistSongKey,
                serializer = serializer<StoredSong>(),
                storageLayout = CacheStorageLayout.HashValue,
            )

            cache(songs.key(3, 1)) {
                StoredSong(1, "First")
            }
            cache(songs.key(3, 2)) {
                StoredSong(2, "Second")
            }
            cache(songs.key(4, 3)) {
                StoredSong(3, "Other Artist")
            }

            cache.invalidate(songs.keyPart(artistKey(3)))

            assertNull(store.hashMap["songs-by-artist-cache:3"])
            assertEquals("""{"id":3,"title":"Other Artist"}""", store.hashMap["songs-by-artist-cache:4"]?.get("3"))
        }
    }
}
