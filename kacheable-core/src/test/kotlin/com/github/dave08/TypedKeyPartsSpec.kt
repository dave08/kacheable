@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheStorageLayout.HashValue
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull

val TypedKeyPartsSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("main and secondary key parts support grouped invalidation") {
            val artistId = 7
            val otherArtistId = 8

            cache(typedSongPageCache.key(artistId, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page 1")
            }
            cache(typedSongPageCache.key(artistId, PageWindow(10, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page 2")
            }
            cache(typedSongPageCache.key(otherArtistId, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(otherArtistId, "Other Song")
            }

            cache.invalidate(typedSongPageCache.keyPart(artistId))

            assertNull(store.hashMap["song-page-cache:7"])
            assertEquals("""{"id":8,"title":"Other Song"}""", store.hashMap["song-page-cache:8"]?.get("0,10,en"))
        }

        test("entry refs expose logical key parts") {
            val artistId = 7
            val entryRef = typedSongPageCache.key(artistId, PageWindow(0, 10), "en")
            val partRef = typedSongPageCache.keyPart(artistId)

            assertEquals(HashValue, entryRef.storageLayout)
            assertEquals(listOf(7), entryRef.keyGroups.main.toList())
            assertEquals(listOf(0, 10, "en"), entryRef.keyGroups.secondary?.toList())
            assertEquals(listOf(7, 0, 10, "en"), entryRef.keyGroups.flattened.toList())
            assertEquals(listOf("7", "*", "*", "*"), partRef.args.toList().map(Any::toString))
            assertEquals(listOf(7), partRef.keyGroups.main.toList())
            assertEquals(listOf("*", "*", "*"), partRef.keyGroups.secondary?.toList()?.map(Any::toString))
            assertEquals(listOf("7", "*", "*", "*"), partRef.keyGroups.flattened.toList().map(Any::toString))
        }

        test("main keys can use explicit mappers for value classes") {
            val songByIdCache = mainKey("song-cache", songIdKey, storedAs = CacheStorage.HashMap)
            val songId = SongId(13)

            cache(songByIdCache.key(songId), returnsAs = value<TestSong>()) {
                TestSong(13, "Mapped Id")
            }

            cache.invalidate(songByIdCache.keyPart(songId))

            assertNull(store.get("song-cache:13"))
        }

        test("secondary mappers can encode multiple fields from one parameter") {
            val songId = SongId(4)
            val otherSongId = SongId(5)

            cache(typedSongSectionCache.key(songId, SongSection(songId, "lyrics")), returnsAs = value<TestSong>()) {
                TestSong(4, "Section A")
            }
            cache(typedSongSectionCache.key(songId, SongSection(songId, "credits")), returnsAs = value<TestSong>()) {
                TestSong(4, "Section B")
            }
            cache(typedSongSectionCache.key(otherSongId, SongSection(otherSongId, "lyrics")), returnsAs = value<TestSong>()) {
                TestSong(5, "Other Section")
            }

            cache.invalidate(typedSongSectionCache.keyPart(songId))

            assertNull(store.hashMap["song-section-cache:4"])
            assertEquals("""{"id":5,"title":"Other Section"}""", store.hashMap["song-section-cache:5"]?.get("5,lyrics"))
        }
    }

    testFixture {
        SuspendCacheFixture(namingStrategy = structuredKeyStrategy)
    } asContextForEach {
        test("structured key parts respect a custom name strategy") {
            val artistId = 7

            val result = cache(typedSongPageCache.key(artistId, PageWindow(2, 25), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page A")
            }

            assertEquals(TestSong(artistId, "Page A"), result)
            assertEquals(
                """{"id":7,"title":"Page A"}""",
                store.hashMap["song-page-cache|song=7"]?.get("2,25,en"),
            )
        }

        test("secondary composition can cover five function parameters under one main key") {
            val artistId = 7
            val otherArtistId = 9

            cache(typedWideSongCache.key(artistId, "favorites", "recent", 25, "us", "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Wide 1")
            }
            cache(typedWideSongCache.key(artistId, "favorites", "popular", 25, "us", "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Wide 2")
            }
            cache(typedWideSongCache.key(otherArtistId, "favorites", "recent", 25, "us", "en"), returnsAs = value<TestSong>()) {
                TestSong(otherArtistId, "Wide Other")
            }

            cache.invalidate(typedWideSongCache.keyPart(artistId))

            assertNull(store.hashMap["wide-cache:7"])
            assertEquals("""{"id":9,"title":"Wide Other"}""", store.hashMap["wide-cache:9"]?.get("favorites,recent,25,us,en"))
        }
    }
}
