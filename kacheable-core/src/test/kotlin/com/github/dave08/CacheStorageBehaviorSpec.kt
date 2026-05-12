package com.github.dave08

import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.returns
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class StoredSong(val id: Int, val title: String)

private data class Page(val offset: Int, val limit: Int)

private val pageSliceKey = keyPart<Page>(Page::offset, Page::limit)
private val artistSongIdKey = keyPart<Int>()
private val pageOwnerKey = keyPart<Int>("pageOwner")
private val artistIdKey = keyPart<Int>("artistId")
private val songsByPageCache = cacheKey(
    "songs-by-page-cache",
    returns<List<StoredSong>>(),
    key = partitioned(partition = pageOwnerKey, key = pageSliceKey),
)
private val songsByArtistCache = cacheKey(
    "songs-by-artist-cache",
    returns<StoredSong>(),
    key = partitioned(partition = artistIdKey, key = artistSongIdKey),
)

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

            val first = cache(songsByPageCache(3, Page(0, 10))) {
                calls++
                listOf(StoredSong(1, "First"))
            }
            val second = cache(songsByPageCache(3, Page(0, 10))) {
                calls++
                listOf(StoredSong(2, "Second"))
            }

            assertEquals(first, second)
            assertEquals(1, calls)
            assertNull(store.get("songs-by-page-cache:3,0,10"))
            assertEquals("""[{"id":1,"title":"First"}]""", store.hashMap["songs-by-page-cache:3"]?.get("0,10"))
        }

        test("hash-backed stored caches can invalidate one field without deleting siblings") {
            cache(songsByPageCache(3, Page(0, 10))) {
                listOf(StoredSong(1, "First"))
            }
            cache(songsByPageCache(3, Page(10, 10))) {
                listOf(StoredSong(2, "Second"))
            }

            cache.invalidate(songsByPageCache(3, Page(0, 10)))

            assertNull(store.hashMap["songs-by-page-cache:3"]?.get("0,10"))
            assertEquals("""[{"id":2,"title":"Second"}]""", store.hashMap["songs-by-page-cache:3"]?.get("10,10"))
        }

        test("hash-backed stored caches can invalidate all fields under the primary key") {
            cache(songsByArtistCache(3, 1)) {
                StoredSong(1, "First")
            }
            cache(songsByArtistCache(3, 2)) {
                StoredSong(2, "Second")
            }
            cache(songsByArtistCache(4, 3)) {
                StoredSong(3, "Other Artist")
            }

            cache.invalidate(songsByArtistCache.partition(3))

            assertNull(store.hashMap["songs-by-artist-cache:3"])
            assertEquals("""{"id":3,"title":"Other Artist"}""", store.hashMap["songs-by-artist-cache:4"]?.get("3"))
        }
    }
}
