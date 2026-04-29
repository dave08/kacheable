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
import kotlin.test.assertEquals
import kotlin.test.assertNull

val TypedExactApiSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("raw runtime cache calls still cache exact values") {
            var calls = 0

            val first = cache<TestSong>("song-cache", 7) {
                calls++
                TestSong(7, "Track")
            }
            val second = cache<TestSong>("song-cache", 7) {
                calls++
                TestSong(7, "Other")
            }

            assertEquals(TestSong(7, "Track"), first)
            assertEquals(TestSong(7, "Track"), second)
            assertEquals(1, calls)
            assertEquals("""{"id":7,"title":"Track"}""", store.get("song-cache:7"))
        }

        test("raw runtime invalidation removes one exact cached value") {
            cache<TestSong>("song-cache", 3) { TestSong(3, "Melody") }

            cache.invalidate("song-cache" to listOf(3)) {}

            assertNull(store.get("song-cache:3"))
        }

        test("typed string storage caches exact values at flat keys") {
            val songCache = entryKey<Int>("song-cache", storedAs = CacheStorage.String)
            var calls = 0

            val first = cache(songCache.key(21), returnsAs = value<TestSong>()) {
                calls++
                TestSong(21, "Typed String")
            }
            val second = cache(songCache.key(21), returnsAs = value<TestSong>()) {
                calls++
                TestSong(21, "Other")
            }

            assertEquals(TestSong(21, "Typed String"), first)
            assertEquals(TestSong(21, "Typed String"), second)
            assertEquals(1, calls)
            assertEquals("""{"id":21,"title":"Typed String"}""", store.get("song-cache:21"))
        }

        test("typed string storage composes same-level key parts into flat keys") {
            val songId by keyPart<Int>()
            val locale = keyPart<String>("locale")
            val songCache = entryKey("song-cache", songId + locale, storedAs = CacheStorage.String)

            cache(songCache.key(21, "en"), returnsAs = value<TestSong>()) {
                TestSong(21, "Typed String")
            }

            assertEquals("""{"id":21,"title":"Typed String"}""", store.get("song-cache:21,en"))
        }

        test("typed string storage invalidation removes exact flat values") {
            val songCache = entryKey<Int>("song-cache", storedAs = CacheStorage.String)

            cache(songCache.key(21), returnsAs = value<TestSong>()) {
                TestSong(21, "Typed String")
            }

            cache.invalidate(songCache.keyPart(21))

            assertNull(store.get("song-cache:21"))
        }

        test("typed string storage exact entry refs invalidate one flat value") {
            val songCache = entryKey<Int>("song-cache", storedAs = CacheStorage.String)

            cache(songCache.key(21), returnsAs = value<TestSong>()) {
                TestSong(21, "Typed String")
            }
            cache(songCache.key(22), returnsAs = value<TestSong>()) {
                TestSong(22, "Other")
            }

            cache.invalidate(songCache.key(21))

            assertNull(store.get("song-cache:21"))
            assertEquals("""{"id":22,"title":"Other"}""", store.get("song-cache:22"))
        }

        test("stored exact refs can still use typed return views") {
            val songCache = entryKey<Int>("song-cache", storedAs = CacheStorage.HashMap)
            val songId = 21

            val result = cache(songCache.key(songId), returnsAs = value<TestSong>()) {
                TestSong(songId, "Typed")
            }

            assertEquals(TestSong(songId, "Typed"), result)
            assertEquals("""{"id":21,"title":"Typed"}""", store.get("song-cache:21"))
        }

        test("cacheIf can skip saving a typed cache result") {
            val songCache = entryKey<Int>("song-cache", storedAs = CacheStorage.HashMap)

            val result = cache(songCache.key(9), returnsAs = value<TestSong>(), cacheIf = { false }) {
                TestSong(9, "Live")
            }

            assertEquals(TestSong(9, "Live"), result)
            assertNull(store.get("song-cache:9"))
        }
    }

    testFixture {
        SuspendCacheFixture(namingStrategy = bracketedKeyStrategy)
    } asContextForEach {
        test("typed string storage uses the configured naming strategy for exact keys") {
            val songCache = entryKey<Int>("song-cache", storedAs = CacheStorage.String)

            cache(songCache.key(21), returnsAs = value<TestSong>()) {
                TestSong(21, "Typed String")
            }

            assertEquals("""{"id":21,"title":"Typed String"}""", store.get("song-cache[21]"))
        }
    }
}
