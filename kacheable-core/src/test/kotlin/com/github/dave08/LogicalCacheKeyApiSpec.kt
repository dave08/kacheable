@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.defaultCacheNamingStrategy
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.enumMembershipStorage
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.indexedValueStorage
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.matchableKeyPart
import com.github.dave08.kacheable.membershipStorage
import com.github.dave08.kacheable.plus
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

val LogicalCacheKeyApiSpec by testSuite {
    testFixture {
        SuspendCacheFixture(
            configs = mapOf("logical-nullable-song" to CacheConfig("logical-nullable-song", nullPlaceholder = "__NULL__")),
        )
    } asContextForEach {
        test("exact logical caches treat class list set and map results as one value") {
            val songId = keyPart<Int>("songId")
            val songCache = cacheKey("logical-song", returns<CachedSong>(), key = exact(songId))
            val listCache = cacheKey("logical-song-list", returns<List<CachedSong>>(), key = exact(songId))
            val setCache = cacheKey("logical-song-set", returns<Set<Int>>(), key = exact(songId))
            val mapCache = cacheKey("logical-song-map", returns<Map<Int, CachedSong>>(), key = exact(songId))

            cache(songCache(7)) { CachedSong(7, "Seven") }
            cache(listCache(7)) { listOf(CachedSong(7, "Seven"), CachedSong(8, "Eight")) }
            cache(setCache(7)) { setOf(7, 8) }
            cache(mapCache(7)) { mapOf(7 to CachedSong(7, "Seven")) }

            store.assertStringValue("logical-song:7", """{"id":7,"title":"Seven"}""")
            store.assertStringValue("logical-song-list:7", """[{"id":7,"title":"Seven"},{"id":8,"title":"Eight"}]""")
            store.assertStringValue("logical-song-set:7", """[7,8]""")
            store.assertStringValue("logical-song-map:7", """{"7":{"id":7,"title":"Seven"}}""")

            cache.invalidate(listCache(7))

            store.assertStringValueMissing("logical-song-list:7")
            assertNull(store.hashMap["logical-song-list:7"])
        }

        test("nullable logical results use value storage and can cache null with a placeholder") {
            val songId = keyPart<Int>("songId")
            val nullableSongCache = cacheKey(
                "logical-nullable-song",
                returns<CachedSong?>(),
                key = exact(songId),
            )

            var calls = 0
            val first = cache(nullableSongCache(7)) {
                calls += 1
                null
            }
            val second = cache(nullableSongCache(7)) {
                error("cached null should prevent this block from running")
            }

            assertNull(first)
            assertNull(second)
            assertEquals(1, calls)
            store.assertStringValue("logical-nullable-song:7", "__NULL__")
        }

        test("nullable key parts preserve key positions instead of omitting nulls") {
            val filter = keyPart<String?>("filter")
            val sort = keyPart<String?>("sort")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val artistsCache = cacheKey(
                "logical-nullable-key-parts",
                returns<List<CachedSong>>(),
                key = exact(filter + sort + page),
            )

            cache(artistsCache(null, "alphabetical", ResultPage(0, 10))) { listOf(CachedSong(7, "Seven")) }
            cache(artistsCache("featured", "alphabetical", ResultPage(0, 10))) { listOf(CachedSong(8, "Eight")) }

            store.assertStringValue("logical-nullable-key-parts:<null>,alphabetical,0,10", """[{"id":7,"title":"Seven"}]""")
            store.assertStringValue("logical-nullable-key-parts:featured,alphabetical,0,10", """[{"id":8,"title":"Eight"}]""")

            cache.invalidate(artistsCache(null, "alphabetical", ResultPage(0, 10)))

            store.assertStringValueMissing("logical-nullable-key-parts:<null>,alphabetical,0,10")
            store.assertStringValue("logical-nullable-key-parts:featured,alphabetical,0,10", """[{"id":8,"title":"Eight"}]""")
        }

        test("default naming strategy can customize nullable key-part rendering") {
            val customFixture = SuspendCacheFixture(
                namingStrategy = defaultCacheNamingStrategy(nullKeyPart = "__NULL_KEY__"),
            )
            val filter = keyPart<String?>("filter")
            val artistsCache = cacheKey(
                "logical-custom-null-key-part",
                returns<List<CachedSong>>(),
                key = exact(filter),
            )

            customFixture.cache(artistsCache(null)) { listOf(CachedSong(7, "Seven")) }

            customFixture.store.assertStringValue("logical-custom-null-key-part:__NULL_KEY__", """[{"id":7,"title":"Seven"}]""")
        }

        test("nullable matchable key parts can target the explicit null value") {
            val artistId = keyPart<Int>("artistId")
            val filter = matchableKeyPart<String?>("filter")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val pageCache = cacheKey(
                "logical-nullable-matchable",
                returns<List<CachedSong>>(),
                key = partitioned(partition = artistId, key = filter + page),
            )

            cache(pageCache(3, null, ResultPage(0, 10))) { listOf(CachedSong(7, "Seven")) }
            cache(pageCache(3, "featured", ResultPage(0, 10))) { listOf(CachedSong(8, "Eight")) }
            cache(pageCache(4, null, ResultPage(0, 10))) { listOf(CachedSong(9, "Nine")) }

            cache.invalidate(pageCache.matching(3, filter(null)))

            assertNull(store.hashMap["logical-nullable-matchable:3"]?.get("<null>,0,10"))
            store.assertHashField("logical-nullable-matchable:3", "featured,0,10", """[{"id":8,"title":"Eight"}]""")
            store.assertHashField("logical-nullable-matchable:4", "<null>,0,10", """[{"id":9,"title":"Nine"}]""")
        }

        test("nullable boolean and enum results do not auto-select membership storage") {
            val songId = keyPart<Int>("songId")
            val accountId = keyPart<Int>("accountId")
            val nullableFollowCache = cacheKey(
                "logical-nullable-follow",
                returns<Boolean?>(),
                key = partitioned(partition = songId, key = accountId),
            )
            val nullableReactionCache = cacheKey(
                "logical-nullable-reaction",
                returns<SongLike?>(),
                key = partitioned(partition = songId, key = accountId),
            )

            cache(nullableFollowCache(3, 7)) { true }
            cache(nullableReactionCache(3, 7)) { SongLike.LIKE }

            store.assertHashField("logical-nullable-follow:3", 7, "true")
            store.assertHashField("logical-nullable-reaction:3", 7, "\"LIKE\"")
            store.assertSetMissing("logical-nullable-follow:3")
            store.assertSetMissing("logical-nullable-reaction:3:${SongLike.LIKE.name}")
        }

        test("logical invalidation block invalidates only after a successful mutation") {
            val songId = keyPart<Int>("songId")
            val songCache = cacheKey("logical-invalidate-after-success", returns<CachedSong>(), key = exact(songId))

            cache(songCache(7)) { CachedSong(7, "Seven") }
            val result = cache.invalidate(songCache(7)) {
                "updated"
            }

            assertEquals("updated", result)
            store.assertStringValueMissing("logical-invalidate-after-success:7")

            cache(songCache(8)) { CachedSong(8, "Eight") }
            assertFailsWith<IllegalStateException> {
                cache.invalidate(songCache(8)) {
                    error("write failed")
                }
            }

            store.assertStringValue("logical-invalidate-after-success:8", """{"id":8,"title":"Eight"}""")
        }

        test("indexed logical caches store class list set and map results as per-entry values") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val classCache = cacheKey("logical-artist-song", returns<CachedSong>(), key = partitioned(partition = artistId, key = songId))
            val listCache = cacheKey("logical-artist-song-list", returns<List<CachedSong>>(), key = partitioned(partition = artistId, key = songId))
            val setCache = cacheKey("logical-artist-song-set", returns<Set<Int>>(), key = partitioned(partition = artistId, key = songId))
            val mapCache = cacheKey("logical-artist-song-map", returns<Map<Int, CachedSong>>(), key = partitioned(partition = artistId, key = songId))

            cache(classCache(3, 7)) { CachedSong(7, "Seven") }
            cache(listCache(3, 7)) { listOf(CachedSong(7, "Seven")) }
            cache(setCache(3, 7)) { setOf(7, 8) }
            cache(mapCache(3, 7)) { mapOf(7 to CachedSong(7, "Seven")) }

            store.assertHashField("logical-artist-song:3", 7, """{"id":7,"title":"Seven"}""")
            store.assertHashField("logical-artist-song-list:3", 7, """[{"id":7,"title":"Seven"}]""")
            store.assertHashField("logical-artist-song-set:3", 7, """[7,8]""")
            store.assertHashField("logical-artist-song-map:3", 7, """{"7":{"id":7,"title":"Seven"}}""")
        }

        test("indexed logical caches invalidate exact entries and whole indexes") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val songCache = cacheKey("logical-artist-song", returns<CachedSong>(), key = partitioned(partition = artistId, key = songId))

            cache(songCache(3, 7)) { CachedSong(7, "Seven") }
            cache(songCache(3, 8)) { CachedSong(8, "Eight") }
            cache(songCache(4, 9)) { CachedSong(9, "Nine") }

            cache.invalidate(songCache(3, 7))

            assertNull(store.hashMap["logical-artist-song:3"]?.get("7"))
            store.assertHashField("logical-artist-song:3", 8, """{"id":8,"title":"Eight"}""")

            cache.invalidate(songCache.partition(3))

            store.assertHashMissing("logical-artist-song:3")
            store.assertHashField("logical-artist-song:4", 9, """{"id":9,"title":"Nine"}""")
        }

        test("mixed logical invalidation refs can be passed together") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val exactCache = cacheKey("logical-mixed-exact", returns<CachedSong>(), key = exact(songId))
            val indexedCache = cacheKey("logical-mixed-indexed", returns<CachedSong>(), key = partitioned(partition = artistId, key = songId))

            cache(exactCache(7)) { CachedSong(7, "Seven") }
            cache(indexedCache(3, 7)) { CachedSong(7, "Seven") }
            cache(indexedCache(3, 8)) { CachedSong(8, "Eight") }
            cache(indexedCache(4, 9)) { CachedSong(9, "Nine") }

            cache.invalidate(exactCache(7), indexedCache.partition(3))

            store.assertStringValueMissing("logical-mixed-exact:7")
            store.assertHashMissing("logical-mixed-indexed:3")
            store.assertHashField("logical-mixed-indexed:4", 9, """{"id":9,"title":"Nine"}""")
        }

        test("matchable entry-key invalidation matches only inside a concrete partition") {
            val artistId by keyPart<Int>()
            val page = keyPart("page", ResultPage::offset, ResultPage::limit)
            val locale = matchableKeyPart<String>("locale")
            val pageCache = cacheKey("logical-artist-pages", returns<List<CachedSong>>(), key = partitioned(
                    partition = artistId,
                    key = page + locale,
                ),
            )

            cache(pageCache(3, ResultPage(0, 10), "en")) { listOf(CachedSong(7, "Seven")) }
            cache(pageCache(3, ResultPage(10, 10), "en")) { listOf(CachedSong(8, "Eight")) }
            cache(pageCache(3, ResultPage(0, 10), "he")) { listOf(CachedSong(9, "Nine")) }
            cache(pageCache(4, ResultPage(0, 10), "en")) { listOf(CachedSong(10, "Ten")) }

            cache.invalidate(pageCache.matching(3, locale("en")))

            assertNull(store.hashMap["logical-artist-pages:3"]?.get("0,10,en"))
            assertNull(store.hashMap["logical-artist-pages:3"]?.get("10,10,en"))
            store.assertHashField("logical-artist-pages:3", "0,10,he", """[{"id":9,"title":"Nine"}]""")
            store.assertHashField("logical-artist-pages:4", "0,10,en", """[{"id":10,"title":"Ten"}]""")
        }

        test("multiple matchable entry-key parts can be matched independently or together") {
            val artistId = keyPart<Int>("artistId")
            val collection = keyPart<String>("collection")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val locale = matchableKeyPart<String>("locale")
            val device = matchableKeyPart<String>("device")
            val pageCache = cacheKey("logical-matchable-variants", returns<List<CachedSong>>(), key = partitioned(
                    partition = artistId + collection,
                    key = page + locale + device,
                ),
            )

            cache(pageCache(3, "top", ResultPage(0, 10), "en", "mobile")) { listOf(CachedSong(7, "Seven")) }
            cache(pageCache(3, "top", ResultPage(10, 10), "en", "desktop")) { listOf(CachedSong(8, "Eight")) }
            cache(pageCache(3, "top", ResultPage(0, 10), "he", "mobile")) { listOf(CachedSong(9, "Nine")) }
            cache(pageCache(4, "top", ResultPage(0, 10), "en", "mobile")) { listOf(CachedSong(10, "Ten")) }

            cache.invalidate(pageCache.matching(3, "top", locale("en")))

            assertNull(store.hashMap["logical-matchable-variants:3,top"]?.get("0,10,en,mobile"))
            assertNull(store.hashMap["logical-matchable-variants:3,top"]?.get("10,10,en,desktop"))
            store.assertHashField("logical-matchable-variants:3,top", "0,10,he,mobile", """[{"id":9,"title":"Nine"}]""")
            store.assertHashField("logical-matchable-variants:4,top", "0,10,en,mobile", """[{"id":10,"title":"Ten"}]""")

            cache(pageCache(3, "top", ResultPage(0, 10), "en", "mobile")) { listOf(CachedSong(7, "Seven")) }
            cache(pageCache(3, "top", ResultPage(10, 10), "en", "desktop")) { listOf(CachedSong(8, "Eight")) }

            cache.invalidate(pageCache.matching(3, "top", locale("en"), device("mobile")))

            assertNull(store.hashMap["logical-matchable-variants:3,top"]?.get("0,10,en,mobile"))
            store.assertHashField("logical-matchable-variants:3,top", "10,10,en,desktop", """[{"id":8,"title":"Eight"}]""")
        }

        test("matching invalidation only accepts matchable key-part values") {
            val artistId = keyPart<Int>("artistId")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val locale = matchableKeyPart<String>("locale")
            val device = matchableKeyPart<String>("device")
            val pageCache = cacheKey("logical-matchable-guard", returns<List<CachedSong>>(), key = partitioned(
                    partition = artistId,
                    key = page + locale,
                ),
            )

            cache.invalidate(pageCache.matching(3, locale("en")))
            assertFailsWith<IllegalArgumentException> {
                pageCache.matching(3, device("mobile"))
            }
        }

        test("logical cache keys keep call sites typed through total arity six") {
            val p1 = keyPart<Int>("p1")
            val p2 = keyPart<String>("p2")
            val p3 = keyPart<Int>("p3")
            val p4 = keyPart<Int>("p4")
            val p5 = keyPart<Int>("p5")
            val p6 = keyPart<Int>("p6")
            val exactCache = cacheKey("logical-exact-arity", returns<CachedSong>(), key = exact(p1 + p2 + p3 + p4 + p5 + p6))

            cache(exactCache(1, "en", 3, 4, 5, 6)) { CachedSong(7, "Seven") }

            store.assertStringValue("logical-exact-arity:1,en,3,4,5,6", """{"id":7,"title":"Seven"}""")

            val i1 = keyPart<Int>("i1")
            val i2 = keyPart<String>("i2")
            val i3 = keyPart<Int>("i3")
            val k1 = keyPart<Int>("k1")
            val k2 = keyPart<String>("k2")
            val k3 = keyPart<Int>("k3")
            val indexedCache = cacheKey("logical-indexed-arity", returns<CachedSong>(), key = partitioned(
                    partition = i1 + i2 + i3,
                    key = k1 + k2 + k3,
                ),
            )

            cache(indexedCache(1, "en", 3, 7, "album", 9)) { CachedSong(7, "Seven") }

            store.assertHashField("logical-indexed-arity:1,en,3", "7,album,9", """{"id":7,"title":"Seven"}""")
            cache.invalidate(indexedCache.partition(1, "en", 3))
            store.assertHashMissing("logical-indexed-arity:1,en,3")

            val variant = matchableKeyPart<String>("variant")
            val scannedCache = cacheKey("logical-scan-arity", returns<CachedSong>(), key = partitioned(
                    partition = i1 + i2,
                    key = k1 + k2 + variant,
                ),
            )

            cache(scannedCache(1, "en", 7, "album", "he")) { CachedSong(7, "Seven") }
            cache(scannedCache(1, "en", 8, "album", "he")) { CachedSong(8, "Eight") }
            cache(scannedCache(1, "en", 7, "album", "en")) { CachedSong(9, "Nine") }

            cache.invalidate(scannedCache.matching(1, "en", variant("he")))

            assertNull(store.hashMap["logical-scan-arity:1,en"]?.get("7,album,he"))
            assertNull(store.hashMap["logical-scan-arity:1,en"]?.get("8,album,he"))
            store.assertHashField("logical-scan-arity:1,en", "7,album,en", """{"id":9,"title":"Nine"}""")
        }

        test("boolean indexed logical caches auto-select membership storage") {
            val artistId = keyPart<Int>("artistId")
            val locale = keyPart<String>("locale")
            val accountId = keyPart<Int>("accountId")
            val followCache = cacheKey("logical-artist-follow", returns<Boolean>(), key = partitioned(
                    partition = artistId + locale,
                    key = accountId,
                ),
            )
            var falseCalls = 0

            val trueResult = cache(followCache(3, "en", 7)) { true }
            val falseResult = cache(followCache(3, "en", 8)) {
                falseCalls++
                false
            }
            val cachedFalse = cache(followCache(3, "en", 8)) {
                falseCalls++
                true
            }

            assertEquals(true, trueResult)
            assertEquals(false, falseResult)
            assertEquals(false, cachedFalse)
            assertEquals(1, falseCalls)
            store.assertSetMember("logical-artist-follow:3,en", 7)
            store.assertSetMember("logical-artist-follow:3,en:__kacheable_non_members", 8)

            cache.invalidate(followCache(3, "en", 7))

            store.assertSetDoesNotContain("logical-artist-follow:3,en", 7)
            store.assertSetMember("logical-artist-follow:3,en:__kacheable_non_members", 8)

            cache.invalidate(followCache.partition(3, "en"))

            store.assertSetMissing("logical-artist-follow:3,en")
            store.assertSetMissing("logical-artist-follow:3,en:__kacheable_non_members")
        }

        test("membership storage can skip caching false results") {
            val artistId = keyPart<Int>("artistId")
            val accountId = keyPart<Int>("accountId")
            val followCache = cacheKey("logical-artist-follow-sparse", returns<Boolean>(), key = partitioned(
                    partition = artistId,
                    key = accountId,
                ),
                storage = membershipStorage(cacheFalse = false),
            )
            var calls = 0

            cache(followCache(3, 7)) {
                calls++
                false
            }
            cache(followCache(3, 7)) {
                calls++
                false
            }

            assertEquals(2, calls)
            store.assertSetMissing("logical-artist-follow-sparse:3:__kacheable_non_members")
        }

        test("enum indexed logical caches auto-select classified membership storage") {
            val songId = keyPart<Int>("songId")
            val accountId = keyPart<Int>("accountId")
            val reactionCache = cacheKey("logical-song-reaction", returns<SongLike>(), key = partitioned(partition = songId, key = accountId))
            var calls = 0

            val first = cache(reactionCache(7, 42)) {
                calls++
                SongLike.LIKE
            }
            val second = cache(reactionCache(7, 42)) {
                calls++
                SongLike.DISLIKE
            }

            assertEquals(SongLike.LIKE, first)
            assertEquals(SongLike.LIKE, second)
            assertEquals(1, calls)
            store.assertSetMember(logicalReactionKey(7, SongLike.LIKE), 42)

            cache.invalidate(reactionCache(7, 42))

            store.assertSetDoesNotContain(logicalReactionKey(7, SongLike.LIKE), 42)

            cache(reactionCache(7, 42)) { SongLike.DISLIKE }
            cache(reactionCache(7, 43)) { SongLike.NONE }
            cache.invalidate(reactionCache.partition(7))

            store.assertSetMissing(logicalReactionKey(7, SongLike.LIKE))
            store.assertSetMissing(logicalReactionKey(7, SongLike.DISLIKE))
            store.assertSetMissing(logicalReactionKey(7, SongLike.NONE))
        }

        test("explicit enum membership storage supports custom enum names") {
            val songId = keyPart<Int>("songId")
            val accountId = keyPart<Int>("accountId")
            val reactionCache = cacheKey("logical-custom-reaction", returns<SongLike>(), key = partitioned(
                    partition = songId,
                    key = accountId,
                ),
                storage = enumMembershipStorage(
                    values = listOf(SongLike.LIKE, SongLike.DISLIKE),
                    valueName = { like -> like.name.lowercase() },
                ),
            )

            cache(reactionCache(7, 42)) { SongLike.DISLIKE }

            store.assertSetMember("logical-custom-reaction:7:dislike", 42)
            store.assertSetMissing("logical-custom-reaction:7:DISLIKE")
        }

        test("power users can force indexed value storage for boolean and enum results") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val accountId = keyPart<Int>("accountId")
            val booleanCache = cacheKey("logical-bool-indexed", returns<Boolean>(), key = partitioned(
                    partition = artistId,
                    key = accountId,
                ),
                storage = indexedValueStorage(),
            )
            val enumCache = cacheKey("logical-enum-indexed", returns<SongLike>(), key = partitioned(
                    partition = songId,
                    key = accountId,
                ),
                storage = indexedValueStorage(),
            )

            cache(booleanCache(3, 7)) { true }
            cache(enumCache(3, 7)) { SongLike.LIKE }

            store.assertHashField("logical-bool-indexed:3", 7, "true")
            store.assertHashField("logical-enum-indexed:3", 7, "\"LIKE\"")
            store.assertSetMissing("logical-bool-indexed:3")
            store.assertSetMissing("logical-enum-indexed:3:${SongLike.LIKE.name}")
        }

        test("matchable entry-key parts force indexed value storage for boolean results under auto") {
            val artistId = keyPart<Int>("artistId")
            val accountId = keyPart<Int>("accountId")
            val locale = matchableKeyPart<String>("locale")
            val followCache = cacheKey("logical-scanned-follow", returns<Boolean>(), key = partitioned(
                    partition = artistId,
                    key = accountId + locale,
                ),
            )

            cache(followCache(3, 7, "en")) { true }

            store.assertHashField("logical-scanned-follow:3", "7,en", "true")
            store.assertSetMissing("logical-scanned-follow:3")
        }
    }

    testFixture {
        BlockingCacheFixture()
    } asContextForEach {
        test("blocking cache supports logical exact indexed boolean and enum caches") {
            val id = keyPart<Int>("id")
            val accountId = keyPart<Int>("accountId")
            val exactCache = cacheKey("logical-blocking-song", returns<CachedSong>(), key = exact(id))
            val indexedCache = cacheKey("logical-blocking-artist-song", returns<CachedSong>(), key = partitioned(partition = id, key = accountId))
            val followCache = cacheKey("logical-blocking-follow", returns<Boolean>(), key = partitioned(partition = id, key = accountId))
            val reactionCache = cacheKey("logical-blocking-reaction", returns<SongLike>(), key = partitioned(partition = id, key = accountId))

            val exact = cache(exactCache(7)) { CachedSong(7, "Seven") }
            val indexed = cache(indexedCache(3, 7)) { CachedSong(7, "Seven") }
            val follows = cache(followCache(3, 7)) { true }
            val reaction = cache(reactionCache(3, 7)) { SongLike.LIKE }

            assertEquals(CachedSong(7, "Seven"), exact)
            assertEquals(CachedSong(7, "Seven"), indexed)
            assertEquals(true, follows)
            assertEquals(SongLike.LIKE, reaction)
            store.assertHashField("logical-blocking-artist-song:3", 7, """{"id":7,"title":"Seven"}""")
            store.assertSetMember("logical-blocking-follow:3", 7)
            store.assertSetMember("logical-blocking-reaction:3:${SongLike.LIKE.name}", 7)
        }
    }

    testFixture {
        SuspendCacheFixture(namingStrategy = logicalNamingStrategy)
    } asContextForEach {
        test("logical cache keys route index and keyed scan parts through the naming strategy") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val locale = matchableKeyPart<String>("locale")
            val exactCache = cacheKey("logical-named-exact", returns<CachedSong>(), key = exact(songId))
            val indexedCache = cacheKey("logical-named-indexed", returns<CachedSong>(), key = partitioned(
                    partition = artistId,
                    key = songId + locale,
                ),
            )

            cache(exactCache(7)) { CachedSong(7, "Seven") }
            cache(indexedCache(3, 7, "en")) { CachedSong(7, "Seven") }

            store.assertStringValue("logical-named-exact|combined=7", """{"id":7,"title":"Seven"}""")
            store.assertHashField(
                "logical-named-indexed|primary=3",
                "secondary=7|en",
                """{"id":7,"title":"Seven"}""",
            )
        }
    }
}

private fun logicalReactionKey(songId: Int, like: SongLike): String = "logical-song-reaction:$songId:${like.name}"

private val logicalNamingStrategy = CacheNamingStrategy { name, primaryParams, secondaryParams ->
    if (secondaryParams.isEmpty()) {
        CacheEntryName.Primary(
            primary = "$name|primary=${primaryParams.joinToString("|")}",
            combined = "$name|combined=${primaryParams.joinToString("|")}",
        )
    } else {
        CacheEntryName.PrimarySecondary(
            primary = "$name|primary=${primaryParams.joinToString("|")}",
            secondary = "secondary=${secondaryParams.joinToString("|")}",
            combine = { _, _ -> "$name|combined=${(primaryParams.asList() + secondaryParams.asList()).joinToString("|")}" },
        )
    }
}
