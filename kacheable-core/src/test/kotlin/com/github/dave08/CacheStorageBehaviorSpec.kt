@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.entryKey
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class StoredSong(val id: Int, val title: String)

private data class Page(val offset: Int, val limit: Int)

private val pageSliceKey = keyPart<Page>(Page::offset, Page::limit)
private val artistSongIdKey = keyPart<Int>()
private val songsByPageCache = entryKey<Int>("songs-by-page-cache", storedAs = CacheStorage.HashMap) + pageSliceKey
private val songsByArtistCache = entryKey<Int>("songs-by-artist-cache", storedAs = CacheStorage.HashMap) + artistSongIdKey

val CacheStorageBehaviorSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("raw runtime cache stores string-backed values at the flattened key") {
            cache<List<StoredSong>>("songs-by-page-cache", 3, 0, 10) {
                listOf(StoredSong(1, "First"))
            }

            assertEquals("""[{"id":1,"title":"First"}]""", store.get("songs-by-page-cache:3,0,10"))
            assertNull(store.hashMap["songs-by-page-cache:3"]?.get("0,10"))
        }

        test("hash-backed stored caches keep secondary parts as fields") {
            var calls = 0

            val first = cache(songsByPageCache.key(3, Page(0, 10)), returnsAs = value<List<StoredSong>>()) {
                calls++
                listOf(StoredSong(1, "First"))
            }
            val second = cache(songsByPageCache.key(3, Page(0, 10)), returnsAs = value<List<StoredSong>>()) {
                calls++
                listOf(StoredSong(2, "Second"))
            }

            assertEquals(first, second)
            assertEquals(1, calls)
            assertNull(store.get("songs-by-page-cache:3,0,10"))
            assertEquals("""[{"id":1,"title":"First"}]""", store.hashMap["songs-by-page-cache:3"]?.get("0,10"))
        }

        test("hash-backed stored caches can invalidate one field without deleting siblings") {
            cache(songsByPageCache.key(3, Page(0, 10)), returnsAs = value<List<StoredSong>>()) {
                listOf(StoredSong(1, "First"))
            }
            cache(songsByPageCache.key(3, Page(10, 10)), returnsAs = value<List<StoredSong>>()) {
                listOf(StoredSong(2, "Second"))
            }

            cache.invalidate(songsByPageCache.key(3, Page(0, 10)))

            assertNull(store.hashMap["songs-by-page-cache:3"]?.get("0,10"))
            assertEquals("""[{"id":2,"title":"Second"}]""", store.hashMap["songs-by-page-cache:3"]?.get("10,10"))
        }

        test("hash-backed stored caches can invalidate all fields under the primary key") {
            cache(songsByArtistCache.key(3, 1), returnsAs = value<StoredSong>()) {
                StoredSong(1, "First")
            }
            cache(songsByArtistCache.key(3, 2), returnsAs = value<StoredSong>()) {
                StoredSong(2, "Second")
            }
            cache(songsByArtistCache.key(4, 3), returnsAs = value<StoredSong>()) {
                StoredSong(3, "Other Artist")
            }

            cache.invalidate(songsByArtistCache.keyPart(3))

            assertNull(store.hashMap["songs-by-artist-cache:3"])
            assertEquals("""{"id":3,"title":"Other Artist"}""", store.hashMap["songs-by-artist-cache:4"]?.get("3"))
        }
    }
}
