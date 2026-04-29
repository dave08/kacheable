@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.entryKey
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull

val BlockingTypedCacheSpec by testSuite {
    testFixture {
        BlockingCacheFixture()
    } asContextForEach {
        test("blocking exact caching still works with stored key refs") {
            val songByIdCache = entryKey<Int>("song-cache", storedAs = CacheStorage.HashMap)
            var calls = 0

            val first = cache(songByIdCache.key(11), returnsAs = value<TestSong>()) {
                calls++
                TestSong(11, "Blocking")
            }
            val second = cache(songByIdCache.key(11), returnsAs = value<TestSong>()) {
                calls++
                TestSong(11, "Other")
            }

            assertEquals(TestSong(11, "Blocking"), first)
            assertEquals(TestSong(11, "Blocking"), second)
            assertEquals(1, calls)
            assertEquals("""{"id":11,"title":"Blocking"}""", store.get("song-cache:11"))
        }

        test("blocking exact caching works with typed string storage") {
            val songByIdCache = entryKey<Int>("song-cache", storedAs = CacheStorage.String)
            var calls = 0

            val first = cache(songByIdCache.key(11), returnsAs = value<TestSong>()) {
                calls++
                TestSong(11, "Blocking String")
            }
            val second = cache(songByIdCache.key(11), returnsAs = value<TestSong>()) {
                calls++
                TestSong(11, "Other")
            }

            assertEquals(TestSong(11, "Blocking String"), first)
            assertEquals(TestSong(11, "Blocking String"), second)
            assertEquals(1, calls)
            assertEquals("""{"id":11,"title":"Blocking String"}""", store.get("song-cache:11"))
        }

        test("blocking typed string storage composes same-level key parts into flat keys") {
            val songId by keyPart<Int>()
            val locale = keyPart<String>("locale")
            val songCache = entryKey("song-cache", songId + locale, storedAs = CacheStorage.String)

            cache(songCache.key(11, "en"), returnsAs = value<TestSong>()) {
                TestSong(11, "Blocking String")
            }

            assertEquals("""{"id":11,"title":"Blocking String"}""", store.get("song-cache:11,en"))
        }

        test("blocking typed string exact entry refs invalidate one flat value") {
            val songByIdCache = entryKey<Int>("song-cache", storedAs = CacheStorage.String)

            cache(songByIdCache.key(11), returnsAs = value<TestSong>()) {
                TestSong(11, "Blocking String")
            }
            cache(songByIdCache.key(12), returnsAs = value<TestSong>()) {
                TestSong(12, "Other")
            }

            cache.invalidate(songByIdCache.key(11))

            assertNull(store.get("song-cache:11"))
            assertEquals("""{"id":12,"title":"Other"}""", store.get("song-cache:12"))
        }

        test("blocking hash entry refs invalidate one stored field") {
            val artistId = 11

            cache(typedSongPageCache.key(artistId, PageWindow(3, 50), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Blocking Page")
            }
            cache(typedSongPageCache.key(artistId, PageWindow(4, 50), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Blocking Page 2")
            }

            cache.invalidate(typedSongPageCache.key(artistId, PageWindow(3, 50), "en"))

            assertNull(store.hashMap["song-page-cache:11"]?.get("3,50,en"))
            assertEquals("""{"id":11,"title":"Blocking Page 2"}""", store.hashMap["song-page-cache:11"]?.get("4,50,en"))
        }
    }

    testFixture {
        BlockingCacheFixture(namingStrategy = structuredKeyStrategy)
    } asContextForEach {
        test("blocking grouped invalidation uses the stored key definition") {
            val artistId = 11

            cache(typedSongPageCache.key(artistId, PageWindow(3, 50), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Blocking Page")
            }
            cache(typedSongPageCache.key(artistId, PageWindow(4, 50), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Blocking Page 2")
            }
            cache(typedSongPageCache.key(12, PageWindow(3, 50), "en"), returnsAs = value<TestSong>()) {
                TestSong(12, "Blocking Other")
            }

            cache.invalidate(typedSongPageCache.keyPart(artistId))

            assertNull(store.hashMap["song-page-cache|song=11"])
            assertEquals(
                """{"id":12,"title":"Blocking Other"}""",
                store.hashMap["song-page-cache|song=12"]?.get("3,50,en"),
            )
        }

        test("blocking partial invalidation uses selected secondary parts without exposing low-level args") {
            val artistId = 11

            cache(namedSongPageCache.key(artistId, PageWindow(3, 50), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Blocking EN")
            }
            cache(namedSongPageCache.key(artistId, PageWindow(4, 50), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Blocking EN 2")
            }
            cache(namedSongPageCache.key(artistId, PageWindow(3, 50), "he"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Blocking HE")
            }

            cache.invalidate(namedSongPageCache.keyPart(artistId, namedLocaleKey("en")))

            assertNull(store.hashMap["named-song-page-cache:11"]?.get("3,50,en"))
            assertNull(store.hashMap["named-song-page-cache:11"]?.get("4,50,en"))
            assertEquals("""{"id":11,"title":"Blocking HE"}""", store.hashMap["named-song-page-cache:11"]?.get("3,50,he"))
        }
    }
}
