@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull

val BlockingTypedCacheSpec by testSuite {
    testFixture {
        BlockingCacheFixture()
    } asContextForEach {
        test("blocking exact caching still works with stored key refs") {
            val songByIdCache = mainKey<Int>("song-cache", storedAs = CacheStorage.HashMap)
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
    }

    testFixture {
        BlockingCacheFixture(getNameStrategy = structuredKeyStrategy)
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
    }
}
