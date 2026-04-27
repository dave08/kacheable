@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.map
import com.github.dave08.kacheable.store.rawStringCacheValueCodec
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val StoredCacheViewSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
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
