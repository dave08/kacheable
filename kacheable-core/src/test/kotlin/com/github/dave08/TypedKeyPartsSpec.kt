@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.argsOf
import com.github.dave08.kacheable.entryKey
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.rawKeyPart
import com.github.dave08.kacheable.times
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private data class DelegatedParts(
    val songId: com.github.dave08.kacheable.KeyPart<Int>,
    val locale: com.github.dave08.kacheable.KeyPart<String>,
)

val TypedKeyPartsSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("primary and secondary key parts support grouped invalidation") {
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

        test("entry refs expose named key parts") {
            val artistId = 7
            val entryRef = typedSongPageCache.key(artistId, PageWindow(0, 10), "en")
            val partRef = typedSongPageCache.keyPart(artistId)

            assertEquals(CacheStorage.HashMap, entryRef.storage)
            assertEquals(listOf(7), entryRef.cacheArgs.primary.toList())
            assertEquals(listOf(0, 10, "en"), entryRef.cacheArgs.secondary?.toList())
            assertEquals(listOf(7, 0, 10, "en"), entryRef.cacheArgs.flattened.toList())
            assertEquals(listOf("7"), partRef.args.toList().map { it.toString() })
            assertEquals(listOf(7), partRef.cacheArgs.primary.toList())
            assertNull(partRef.cacheArgs.secondary)
            assertEquals(listOf("7"), partRef.cacheArgs.flattened.toList().map { it.toString() })
            assertEquals(listOf(listOf(7)), entryRef.cacheArgs.primaryPartArgs.map { it.toList() })
            assertEquals(listOf(null), entryRef.cacheArgs.primaryPartNames)
            assertEquals(listOf(listOf(0, 10), listOf("en")), entryRef.cacheArgs.secondaryPartArgs.map { it.toList() })
            assertEquals(listOf(null, null), entryRef.cacheArgs.secondaryPartNames)
        }

        test("named key parts keep per-part low-level structure") {
            val entryRef = namedSongPageCache.key(7, PageWindow(0, 10), "en")

            assertEquals(listOf(listOf(7)), entryRef.cacheArgs.primaryPartArgs.map { it.toList() })
            assertEquals(listOf("artist"), entryRef.cacheArgs.primaryPartNames)
            assertEquals(listOf(listOf(0, 10), listOf("en")), entryRef.cacheArgs.secondaryPartArgs.map { it.toList() })
            assertEquals(listOf("paging", "locale"), entryRef.cacheArgs.secondaryPartNames)
        }

        test("partial invalidation keeps user-facing part selections separate from low-level cache args") {
            val partRef = namedSongPageCache.keyPart(7, namedLocaleKey("en"))

            assertEquals(listOf(listOf(7)), partRef.cacheArgs.primaryPartArgs.map { it.toList() })
            assertNull(partRef.cacheArgs.secondary)
            assertEquals(
                listOf(listOf("*", "*"), listOf("en")),
                partRef.secondaryPatternPartArgs!!.map { args -> args.toList().map { it.toString() } },
            )
        }

        test("partial invalidation can target one secondary key part without leaking into other entries") {
            val artistId = 7

            cache(namedSongPageCache.key(artistId, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page EN")
            }
            cache(namedSongPageCache.key(artistId, PageWindow(10, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Next EN")
            }
            cache(namedSongPageCache.key(artistId, PageWindow(0, 10), "he"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page HE")
            }

            cache.invalidate(namedSongPageCache.keyPart(artistId, namedLocaleKey("en")))

            assertEquals("""{"id":7,"title":"Page HE"}""", store.hashMap["named-song-page-cache:7"]?.get("0,10,he"))
            assertNull(store.hashMap["named-song-page-cache:7"]?.get("0,10,en"))
            assertNull(store.hashMap["named-song-page-cache:7"]?.get("10,10,en"))
        }

        test("partial invalidation can match a named secondary key part without reusing its original instance") {
            val artistId = 7

            cache(namedSongPageCache.key(artistId, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page EN")
            }
            cache(namedSongPageCache.key(artistId, PageWindow(0, 10), "he"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page HE")
            }

            cache.invalidate(namedSongPageCache.keyPart(artistId, keyPart<String>("locale")("en")))

            assertEquals("""{"id":7,"title":"Page HE"}""", store.hashMap["named-song-page-cache:7"]?.get("0,10,he"))
            assertNull(store.hashMap["named-song-page-cache:7"]?.get("0,10,en"))
        }

        test("partial invalidation can use named primary and secondary selectors") {
            val artistId = 7

            cache(namedSongPageCache.key(artistId, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page EN")
            }
            cache(namedSongPageCache.key(artistId, PageWindow(10, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Next EN")
            }
            cache(namedSongPageCache.key(artistId, PageWindow(0, 10), "he"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page HE")
            }
            cache(namedSongPageCache.key(8, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(8, "Other Artist")
            }

            cache.invalidate(namedSongPageCache.keyPart(namedSongPagePrimary(artistId), namedLocaleKey("en")))

            assertNull(store.hashMap["named-song-page-cache:7"]?.get("0,10,en"))
            assertNull(store.hashMap["named-song-page-cache:7"]?.get("10,10,en"))
            assertEquals("""{"id":7,"title":"Page HE"}""", store.hashMap["named-song-page-cache:7"]?.get("0,10,he"))
            assertEquals("""{"id":8,"title":"Other Artist"}""", store.hashMap["named-song-page-cache:8"]?.get("0,10,en"))
        }

        test("partial invalidation can use named primary selector for grouped hash invalidation") {
            val artistId = 7

            cache(namedSongPageCache.key(artistId, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page EN")
            }
            cache(namedSongPageCache.key(artistId, PageWindow(10, 10), "he"), returnsAs = value<TestSong>()) {
                TestSong(artistId, "Page HE")
            }
            cache(namedSongPageCache.key(8, PageWindow(0, 10), "en"), returnsAs = value<TestSong>()) {
                TestSong(8, "Other Artist")
            }

            cache.invalidate(namedSongPageCache.keyPart(namedSongPagePrimary(artistId)))

            assertNull(store.hashMap["named-song-page-cache:7"])
            assertEquals("""{"id":8,"title":"Other Artist"}""", store.hashMap["named-song-page-cache:8"]?.get("0,10,en"))
        }

        test("partial invalidation rejects secondary selectors without a primary selector") {
            assertFailsWith<IllegalArgumentException> {
                namedSongPageCache.keyPart(namedLocaleKey("en"))
            }
        }

        listOf(
            "reusable unnamed secondary key part" to { typedLocaleKey("en") },
            "anonymous ad hoc secondary key part" to { keyPart<String>()("en") },
        ).forEach { (caseName, selection) ->
            test("partial invalidation rejects $caseName") {
                assertFailsWith<IllegalArgumentException> {
                    typedSongPageCache.keyPart(7, selection())
                }
            }
        }

        test("primary keys can use explicit mappers for value classes") {
            val songByIdCache = entryKey("song-cache", songIdKey, storedAs = CacheStorage.HashMap)
            val songId = SongId(13)

            cache(songByIdCache.key(songId), returnsAs = value<TestSong>()) {
                TestSong(13, "Mapped Id")
            }

            cache.invalidate(songByIdCache.keyPart(songId))

            assertNull(store.get("song-cache:13"))
        }

        test("primary keys can compose multiple key parts without forcing a wrapper type") {
            val songByArtistAndLocale = entryKey(
                "song-cache",
                keyPart<Int>() + keyPart<String>(),
                storedAs = CacheStorage.HashMap,
            )

            cache(songByArtistAndLocale.key(13, "en"), returnsAs = value<TestSong>()) {
                TestSong(13, "Mapped Id")
            }

            cache.invalidate(songByArtistAndLocale.keyPart(13, "en"))

            assertNull(store.get("song-cache:13,en"))
        }

        test("raw key parts provide an escape hatch for untyped key segments") {
            val rawSongsCache = entryKey(
                "raw-song-cache",
                rawKeyPart(),
                storedAs = CacheStorage.HashMap,
            )

            cache(rawSongsCache.key(argsOf(7, "en")), returnsAs = value<TestSong>()) {
                TestSong(7, "Raw")
            }

            assertEquals("""{"id":7,"title":"Raw"}""", store.get("raw-song-cache:7,en"))
        }

        test("delegated key parts pick up property names without pushing naming into low-level cache args") {
            val delegated = DelegatedParts(
                songId = run {
                    val songId by keyPart<Int>()
                    songId
                },
                locale = run {
                    val locale by keyPart<String>()
                    locale
                },
            )
            val delegatedCache = entryKey(
                "delegated-song-cache",
                delegated.songId * delegated.locale,
                storedAs = CacheStorage.HashMap,
            )

            val entryRef = delegatedCache.key(7, "en")

            assertEquals(listOf("songId"), entryRef.cacheArgs.primaryPartNames)
            assertEquals(listOf("locale"), entryRef.cacheArgs.secondaryPartNames)
            assertEquals(listOf(7), entryRef.cacheArgs.primary.toList())
            assertEquals(listOf("en"), entryRef.cacheArgs.secondary?.toList())
        }

        listOf(
            "hash primary composition" to {
                entryKey(
                    "duplicate-hash-primary-cache",
                    keyPart<Int>("songId") + keyPart<String>("songId"),
                    storedAs = CacheStorage.HashMap,
                )
            },
            "hash primary-secondary composition" to {
                entryKey(
                    "duplicate-hash-layered-cache",
                    keyPart<Int>("songId") * keyPart<String>("songId"),
                    storedAs = CacheStorage.HashMap,
                )
            },
            "hash secondary composition" to {
                entryKey(
                    "duplicate-hash-secondary-cache",
                    keyPart<Int>("artist") * (keyPart<String>("locale") + keyPart<String>("locale")),
                    storedAs = CacheStorage.HashMap,
                )
            },
            "set primary-secondary composition" to {
                entryKey(
                    "duplicate-set-layered-cache",
                    keyPart<Int>("songId") * keyPart<String>("songId"),
                    storedAs = CacheStorage.Set,
                )
            },
        ).forEach { (caseName, buildEntryRef) ->
            test("entry keys reject duplicate named parts for $caseName") {
                assertFailsWith<IllegalArgumentException> {
                    buildEntryRef()
                }
            }
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

        test("secondary composition can cover five function parameters under one primary key") {
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
