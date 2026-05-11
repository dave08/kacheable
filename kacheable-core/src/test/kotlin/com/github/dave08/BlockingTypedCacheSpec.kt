@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.entryKey
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.enumMember
import com.github.dave08.kacheable.isMember
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.map
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.rawCacheEntry
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.times
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

        test("blocking raw entry refs can be invalidated with typed refs") {
            val songId = keyPart<Int>("songId")
            val songByIdCache = cacheKey("song-cache", returns<TestSong>(), key = exact(songId))

            cache("song-cache", 11) { TestSong(11, "Raw") }
            cache(songByIdCache(12)) {
                TestSong(12, "Typed")
            }

            cache.invalidate(rawCacheEntry("song-cache", 11), songByIdCache(12))

            assertNull(store.get("song-cache:11"))
            assertNull(store.get("song-cache:12"))
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

        test("blocking storage-typed entry refs accept capability-compatible return views") {
            val stringCache = entryKey<Int>("song-cache", storedAs = CacheStorage.String)
            val groupedCache = entryKey("artist-songs-cache", keyPart<Int>("artist") * keyPart<Int>("song"), storedAs = CacheStorage.HashMap)
            val membershipCache = entryKey("artist-follow-cache", keyPart<Int>("artist") * keyPart<Int>("account"), storedAs = CacheStorage.Set)

            val stringRef: StoredCacheEntryRef<CacheStorage.String> = stringCache.key(21)
            val groupedRef: StoredCacheEntryRef<CacheStorage.HashMap> = groupedCache.key(7)
            val membershipRef: StoredCacheEntryRef<CacheStorage.Set> = membershipCache.key(7, 42)

            val song = cache(stringRef, returnsAs = value<TestSong>()) {
                TestSong(21, "Typed String")
            }
            val grouped = cache(groupedRef, returnsAs = map<Int, TestSong>()) {
                mapOf(42 to TestSong(42, "Grouped"))
            }
            val member = cache(membershipRef, returnsAs = isMember()) {
                true
            }

            assertEquals(TestSong(21, "Typed String"), song)
            assertEquals(mapOf(42 to TestSong(42, "Grouped")), grouped)
            assertEquals(true, member)
        }

        test("blocking storage-typed set entry refs accept enum membership return views") {
            val classifiedCache = entryKey("song-like-cache", keyPart<Int>("song") * keyPart<Int>("account"), storedAs = CacheStorage.Set)
            val membershipRef: StoredCacheEntryRef<CacheStorage.Set> = classifiedCache.key(7, 42)

            val result = cache(membershipRef, returnsAs = enumMember<SongLike>()) {
                SongLike.DISLIKE
            }

            assertEquals(SongLike.DISLIKE, result)
            store.assertSetMember(songLikeKey(7, SongLike.DISLIKE), 42)
        }

        test("blocking grouped set keys invalidate by primary key through the unified key family") {
            val groupedSetCache = entryKey("artist-follow-cache", keyPart<Int>("artist") * keyPart<Int>("account"), storedAs = CacheStorage.Set)

            cache(groupedSetCache.key(7, 41), returnsAs = isMember()) { true }
            cache(groupedSetCache.key(7, 42), returnsAs = isMember()) { true }
            cache(groupedSetCache.key(8, 50), returnsAs = isMember()) { true }

            cache.invalidate(groupedSetCache.keyPart(7))

            store.assertSetMissing("artist-follow-cache:7")
            store.assertSetMember("artist-follow-cache:8", 50)
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
