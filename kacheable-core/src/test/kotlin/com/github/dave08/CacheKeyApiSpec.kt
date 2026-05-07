@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.blocking.invalidate
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

val CacheKeyApiSpec by testSuite {
    testFixture {
        SuspendCacheFixture(
            configs = mapOf("cache-key-nullable-song" to CacheConfig("cache-key-nullable-song", nullPlaceholder = "__NULL__")),
        )
    } asContextForEach {
        test("zero-argument exact cache keys cache one named value") {
            val settingsCache = cacheKey("cache-key-settings", returns<CachedSong>(), key = exact())

            var calls = 0
            val first = cache(settingsCache()) {
                calls += 1
                CachedSong(1, "Settings")
            }
            val second = cache(settingsCache()) {
                error("named value should already be cached")
            }

            assertEquals(CachedSong(1, "Settings"), first)
            assertEquals(CachedSong(1, "Settings"), second)
            assertEquals(1, calls)
            store.assertStringValue("cache-key-settings", """{"id":1,"title":"Settings"}""")

            cache.invalidate(settingsCache.all())

            store.assertStringValueMissing("cache-key-settings")
        }

        test("cache key definitions and invalidation refs do not require result serializers") {
            val podcastId = keyPart<Int>("podcastId")
            val podcastCache = cacheKey(
                "cache-key-unserializable-podcast",
                returns<UnserializablePodcast>(),
                key = exact(podcastId),
            )

            store.set("cache-key-unserializable-podcast:7", """{"id":7,"title":"Seven"}""")

            cache.invalidate(podcastCache(7))

            store.assertStringValueMissing("cache-key-unserializable-podcast:7")
        }

        test("exact cache keys treat class list set and map results as one value") {
            val songId = keyPart<Int>("songId")
            val songCache = cacheKey("cache-key-song", returns<CachedSong>(), key = exact(songId))
            val listCache = cacheKey("cache-key-song-list", returns<List<CachedSong>>(), key = exact(songId))
            val setCache = cacheKey("cache-key-song-set", returns<Set<Int>>(), key = exact(songId))
            val mapCache = cacheKey("cache-key-song-map", returns<Map<Int, CachedSong>>(), key = exact(songId))

            cache(songCache(7)) { CachedSong(7, "Seven") }
            cache(listCache(7)) { listOf(CachedSong(7, "Seven"), CachedSong(8, "Eight")) }
            cache(setCache(7)) { setOf(7, 8) }
            cache(mapCache(7)) { mapOf(7 to CachedSong(7, "Seven")) }

            store.assertStringValue("cache-key-song:7", """{"id":7,"title":"Seven"}""")
            store.assertStringValue("cache-key-song-list:7", """[{"id":7,"title":"Seven"},{"id":8,"title":"Eight"}]""")
            store.assertStringValue("cache-key-song-set:7", """[7,8]""")
            store.assertStringValue("cache-key-song-map:7", """{"7":{"id":7,"title":"Seven"}}""")

            cache.invalidate(listCache(7))

            store.assertStringValueMissing("cache-key-song-list:7")
            assertNull(store.hashMap["cache-key-song-list:7"])
        }

        test("nullable cache results use value storage and can cache null with a placeholder") {
            val songId = keyPart<Int>("songId")
            val nullableSongCache = cacheKey(
                "cache-key-nullable-song",
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
            store.assertStringValue("cache-key-nullable-song:7", "__NULL__")
        }

        test("nullable key parts preserve key positions instead of omitting nulls") {
            val filter = keyPart<String?>("filter")
            val sort = keyPart<String?>("sort")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val artistsCache = cacheKey(
                "cache-key-nullable-key-parts",
                returns<List<CachedSong>>(),
                key = exact(filter + sort + page),
            )

            cache(artistsCache(null, "alphabetical", ResultPage(0, 10))) { listOf(CachedSong(7, "Seven")) }
            cache(artistsCache("featured", "alphabetical", ResultPage(0, 10))) { listOf(CachedSong(8, "Eight")) }

            store.assertStringValue("cache-key-nullable-key-parts:<null>,alphabetical,0,10", """[{"id":7,"title":"Seven"}]""")
            store.assertStringValue("cache-key-nullable-key-parts:featured,alphabetical,0,10", """[{"id":8,"title":"Eight"}]""")

            cache.invalidate(artistsCache(null, "alphabetical", ResultPage(0, 10)))

            store.assertStringValueMissing("cache-key-nullable-key-parts:<null>,alphabetical,0,10")
            store.assertStringValue("cache-key-nullable-key-parts:featured,alphabetical,0,10", """[{"id":8,"title":"Eight"}]""")
        }

        test("default naming strategy can customize nullable key-part rendering") {
            val customFixture = SuspendCacheFixture(
                namingStrategy = defaultCacheNamingStrategy(nullKeyPart = "__NULL_KEY__"),
            )
            val filter = keyPart<String?>("filter")
            val artistsCache = cacheKey(
                "cache-key-custom-null-key-part",
                returns<List<CachedSong>>(),
                key = exact(filter),
            )

            customFixture.cache(artistsCache(null)) { listOf(CachedSong(7, "Seven")) }

            customFixture.store.assertStringValue("cache-key-custom-null-key-part:__NULL_KEY__", """[{"id":7,"title":"Seven"}]""")
        }

        test("nullable matchable key parts can target the explicit null value") {
            val artistId = keyPart<Int>("artistId")
            val filter = matchableKeyPart<String?>("filter")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val pageCache = cacheKey(
                "cache-key-nullable-matchable",
                returns<List<CachedSong>>(),
                key = partitioned(partition = artistId, key = filter + page),
            )

            cache(pageCache(3, null, ResultPage(0, 10))) { listOf(CachedSong(7, "Seven")) }
            cache(pageCache(3, "featured", ResultPage(0, 10))) { listOf(CachedSong(8, "Eight")) }
            cache(pageCache(4, null, ResultPage(0, 10))) { listOf(CachedSong(9, "Nine")) }

            cache.invalidate(pageCache.matching(3, filter(null)))

            assertNull(store.hashMap["cache-key-nullable-matchable:3"]?.get("<null>,0,10"))
            store.assertHashField("cache-key-nullable-matchable:3", "featured,0,10", """[{"id":8,"title":"Eight"}]""")
            store.assertHashField("cache-key-nullable-matchable:4", "<null>,0,10", """[{"id":9,"title":"Nine"}]""")
        }

        test("nullable boolean and enum results do not auto-select membership storage") {
            val songId = keyPart<Int>("songId")
            val accountId = keyPart<Int>("accountId")
            val nullableFollowCache = cacheKey(
                "cache-key-nullable-follow",
                returns<Boolean?>(),
                key = partitioned(partition = songId, key = accountId),
            )
            val nullableReactionCache = cacheKey(
                "cache-key-nullable-reaction",
                returns<SongLike?>(),
                key = partitioned(partition = songId, key = accountId),
            )

            cache(nullableFollowCache(3, 7)) { true }
            cache(nullableReactionCache(3, 7)) { SongLike.LIKE }

            store.assertHashField("cache-key-nullable-follow:3", 7, "true")
            store.assertHashField("cache-key-nullable-reaction:3", 7, "\"LIKE\"")
            store.assertSetMissing("cache-key-nullable-follow:3")
            store.assertSetMissing("cache-key-nullable-reaction:3:${SongLike.LIKE.name}")
        }

        test("cache-key invalidation block invalidates only after a successful mutation") {
            val songId = keyPart<Int>("songId")
            val songCache = cacheKey("cache-key-invalidate-after-success", returns<CachedSong>(), key = exact(songId))

            cache(songCache(7)) { CachedSong(7, "Seven") }
            val result = cache.invalidate(songCache(7)) {
                "updated"
            }

            assertEquals("updated", result)
            store.assertStringValueMissing("cache-key-invalidate-after-success:7")

            cache(songCache(8)) { CachedSong(8, "Eight") }
            assertFailsWith<IllegalStateException> {
                cache.invalidate(songCache(8)) {
                    error("write failed")
                }
            }

            store.assertStringValue("cache-key-invalidate-after-success:8", """{"id":8,"title":"Eight"}""")
        }

        test("indexed cache keys store class list set and map results as per-entry values") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val classCache = cacheKey("cache-key-artist-song", returns<CachedSong>(), key = partitioned(partition = artistId, key = songId))
            val listCache = cacheKey("cache-key-artist-song-list", returns<List<CachedSong>>(), key = partitioned(partition = artistId, key = songId))
            val setCache = cacheKey("cache-key-artist-song-set", returns<Set<Int>>(), key = partitioned(partition = artistId, key = songId))
            val mapCache = cacheKey("cache-key-artist-song-map", returns<Map<Int, CachedSong>>(), key = partitioned(partition = artistId, key = songId))

            cache(classCache(3, 7)) { CachedSong(7, "Seven") }
            cache(listCache(3, 7)) { listOf(CachedSong(7, "Seven")) }
            cache(setCache(3, 7)) { setOf(7, 8) }
            cache(mapCache(3, 7)) { mapOf(7 to CachedSong(7, "Seven")) }

            store.assertHashField("cache-key-artist-song:3", 7, """{"id":7,"title":"Seven"}""")
            store.assertHashField("cache-key-artist-song-list:3", 7, """[{"id":7,"title":"Seven"}]""")
            store.assertHashField("cache-key-artist-song-set:3", 7, """[7,8]""")
            store.assertHashField("cache-key-artist-song-map:3", 7, """{"7":{"id":7,"title":"Seven"}}""")
        }

        test("indexed cache keys invalidate exact entries and whole indexes") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val songCache = cacheKey("cache-key-artist-song", returns<CachedSong>(), key = partitioned(partition = artistId, key = songId))

            cache(songCache(3, 7)) { CachedSong(7, "Seven") }
            cache(songCache(3, 8)) { CachedSong(8, "Eight") }
            cache(songCache(4, 9)) { CachedSong(9, "Nine") }

            cache.invalidate(songCache(3, 7))

            assertNull(store.hashMap["cache-key-artist-song:3"]?.get("7"))
            store.assertHashField("cache-key-artist-song:3", 8, """{"id":8,"title":"Eight"}""")

            cache.invalidate(songCache.partition(3))

            store.assertHashMissing("cache-key-artist-song:3")
            store.assertHashField("cache-key-artist-song:4", 9, """{"id":9,"title":"Nine"}""")
        }

        test("mixed cache-key invalidation refs can be passed together") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val exactCache = cacheKey("cache-key-mixed-exact", returns<CachedSong>(), key = exact(songId))
            val indexedCache = cacheKey("cache-key-mixed-indexed", returns<CachedSong>(), key = partitioned(partition = artistId, key = songId))

            cache(exactCache(7)) { CachedSong(7, "Seven") }
            cache(indexedCache(3, 7)) { CachedSong(7, "Seven") }
            cache(indexedCache(3, 8)) { CachedSong(8, "Eight") }
            cache(indexedCache(4, 9)) { CachedSong(9, "Nine") }

            cache.invalidate(exactCache(7), indexedCache.partition(3))

            store.assertStringValueMissing("cache-key-mixed-exact:7")
            store.assertHashMissing("cache-key-mixed-indexed:3")
            store.assertHashField("cache-key-mixed-indexed:4", 9, """{"id":9,"title":"Nine"}""")
        }

        test("matchable entry-key invalidation matches only inside a concrete partition") {
            val artistId by keyPart<Int>()
            val page = keyPart("page", ResultPage::offset, ResultPage::limit)
            val locale = matchableKeyPart<String>("locale")
            val pageCache = cacheKey("cache-key-artist-pages", returns<List<CachedSong>>(), key = partitioned(
                    partition = artistId,
                    key = page + locale,
                ),
            )

            cache(pageCache(3, ResultPage(0, 10), "en")) { listOf(CachedSong(7, "Seven")) }
            cache(pageCache(3, ResultPage(10, 10), "en")) { listOf(CachedSong(8, "Eight")) }
            cache(pageCache(3, ResultPage(0, 10), "he")) { listOf(CachedSong(9, "Nine")) }
            cache(pageCache(4, ResultPage(0, 10), "en")) { listOf(CachedSong(10, "Ten")) }

            cache.invalidate(pageCache.matching(3, locale("en")))

            assertNull(store.hashMap["cache-key-artist-pages:3"]?.get("0,10,en"))
            assertNull(store.hashMap["cache-key-artist-pages:3"]?.get("10,10,en"))
            store.assertHashField("cache-key-artist-pages:3", "0,10,he", """[{"id":9,"title":"Nine"}]""")
            store.assertHashField("cache-key-artist-pages:4", "0,10,en", """[{"id":10,"title":"Ten"}]""")
        }

        test("multiple matchable entry-key parts can be matched independently or together") {
            val artistId = keyPart<Int>("artistId")
            val collection = keyPart<String>("collection")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val locale = matchableKeyPart<String>("locale")
            val device = matchableKeyPart<String>("device")
            val pageCache = cacheKey("cache-key-matchable-variants", returns<List<CachedSong>>(), key = partitioned(
                    partition = artistId + collection,
                    key = page + locale + device,
                ),
            )

            cache(pageCache(3, "top", ResultPage(0, 10), "en", "mobile")) { listOf(CachedSong(7, "Seven")) }
            cache(pageCache(3, "top", ResultPage(10, 10), "en", "desktop")) { listOf(CachedSong(8, "Eight")) }
            cache(pageCache(3, "top", ResultPage(0, 10), "he", "mobile")) { listOf(CachedSong(9, "Nine")) }
            cache(pageCache(4, "top", ResultPage(0, 10), "en", "mobile")) { listOf(CachedSong(10, "Ten")) }

            cache.invalidate(pageCache.matching(3, "top", locale("en")))

            assertNull(store.hashMap["cache-key-matchable-variants:3,top"]?.get("0,10,en,mobile"))
            assertNull(store.hashMap["cache-key-matchable-variants:3,top"]?.get("10,10,en,desktop"))
            store.assertHashField("cache-key-matchable-variants:3,top", "0,10,he,mobile", """[{"id":9,"title":"Nine"}]""")
            store.assertHashField("cache-key-matchable-variants:4,top", "0,10,en,mobile", """[{"id":10,"title":"Ten"}]""")

            cache(pageCache(3, "top", ResultPage(0, 10), "en", "mobile")) { listOf(CachedSong(7, "Seven")) }
            cache(pageCache(3, "top", ResultPage(10, 10), "en", "desktop")) { listOf(CachedSong(8, "Eight")) }

            cache.invalidate(pageCache.matching(3, "top", locale("en"), device("mobile")))

            assertNull(store.hashMap["cache-key-matchable-variants:3,top"]?.get("0,10,en,mobile"))
            store.assertHashField("cache-key-matchable-variants:3,top", "10,10,en,desktop", """[{"id":8,"title":"Eight"}]""")
        }

        test("matching invalidation only accepts matchable key-part values") {
            val artistId = keyPart<Int>("artistId")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val locale = matchableKeyPart<String>("locale")
            val device = matchableKeyPart<String>("device")
            val pageCache = cacheKey("cache-key-matchable-guard", returns<List<CachedSong>>(), key = partitioned(
                    partition = artistId,
                    key = page + locale,
                ),
            )

            cache.invalidate(pageCache.matching(3, locale("en")))
            assertFailsWith<IllegalArgumentException> {
                pageCache.matching(3, device("mobile"))
            }
        }

        test("cache keys keep call sites typed through total arity six") {
            val p1 = keyPart<Int>("p1")
            val p2 = keyPart<String>("p2")
            val p3 = keyPart<Int>("p3")
            val p4 = keyPart<Int>("p4")
            val p5 = keyPart<Int>("p5")
            val p6 = keyPart<Int>("p6")
            val exactCache = cacheKey("cache-key-exact-arity", returns<CachedSong>(), key = exact(p1 + p2 + p3 + p4 + p5 + p6))

            cache(exactCache(1, "en", 3, 4, 5, 6)) { CachedSong(7, "Seven") }

            store.assertStringValue("cache-key-exact-arity:1,en,3,4,5,6", """{"id":7,"title":"Seven"}""")

            val i1 = keyPart<Int>("i1")
            val i2 = keyPart<String>("i2")
            val i3 = keyPart<Int>("i3")
            val k1 = keyPart<Int>("k1")
            val k2 = keyPart<String>("k2")
            val k3 = keyPart<Int>("k3")
            val indexedCache = cacheKey("cache-key-indexed-arity", returns<CachedSong>(), key = partitioned(
                    partition = i1 + i2 + i3,
                    key = k1 + k2 + k3,
                ),
            )

            cache(indexedCache(1, "en", 3, 7, "album", 9)) { CachedSong(7, "Seven") }

            store.assertHashField("cache-key-indexed-arity:1,en,3", "7,album,9", """{"id":7,"title":"Seven"}""")
            cache.invalidate(indexedCache.partition(1, "en", 3))
            store.assertHashMissing("cache-key-indexed-arity:1,en,3")

            val variant = matchableKeyPart<String>("variant")
            val scannedCache = cacheKey("cache-key-scan-arity", returns<CachedSong>(), key = partitioned(
                    partition = i1 + i2,
                    key = k1 + k2 + variant,
                ),
            )

            cache(scannedCache(1, "en", 7, "album", "he")) { CachedSong(7, "Seven") }
            cache(scannedCache(1, "en", 8, "album", "he")) { CachedSong(8, "Eight") }
            cache(scannedCache(1, "en", 7, "album", "en")) { CachedSong(9, "Nine") }

            cache.invalidate(scannedCache.matching(1, "en", variant("he")))

            assertNull(store.hashMap["cache-key-scan-arity:1,en"]?.get("7,album,he"))
            assertNull(store.hashMap["cache-key-scan-arity:1,en"]?.get("8,album,he"))
            store.assertHashField("cache-key-scan-arity:1,en", "7,album,en", """{"id":9,"title":"Nine"}""")
        }

        test("boolean indexed cache keys auto-select membership storage") {
            val artistId = keyPart<Int>("artistId")
            val locale = keyPart<String>("locale")
            val accountId = keyPart<Int>("accountId")
            val followCache = cacheKey("cache-key-artist-follow", returns<Boolean>(), key = partitioned(
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
            store.assertSetMember("cache-key-artist-follow:3,en", 7)
            store.assertSetMember("cache-key-artist-follow:3,en:__kacheable_non_members", 8)

            cache.invalidate(followCache(3, "en", 7))

            store.assertSetDoesNotContain("cache-key-artist-follow:3,en", 7)
            store.assertSetMember("cache-key-artist-follow:3,en:__kacheable_non_members", 8)

            cache.invalidate(followCache.partition(3, "en"))

            store.assertSetMissing("cache-key-artist-follow:3,en")
            store.assertSetMissing("cache-key-artist-follow:3,en:__kacheable_non_members")
        }

        test("membership storage can skip caching false results") {
            val artistId = keyPart<Int>("artistId")
            val accountId = keyPart<Int>("accountId")
            val followCache = cacheKey("cache-key-artist-follow-sparse", returns<Boolean>(), key = partitioned(
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
            store.assertSetMissing("cache-key-artist-follow-sparse:3:__kacheable_non_members")
        }

        test("enum indexed cache keys auto-select classified membership storage") {
            val songId = keyPart<Int>("songId")
            val accountId = keyPart<Int>("accountId")
            val reactionCache = cacheKey("cache-key-song-reaction", returns<SongLike>(), key = partitioned(partition = songId, key = accountId))
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
            store.assertSetMember(cacheKeyReactionKey(7, SongLike.LIKE), 42)

            cache.invalidate(reactionCache(7, 42))

            store.assertSetDoesNotContain(cacheKeyReactionKey(7, SongLike.LIKE), 42)

            cache(reactionCache(7, 42)) { SongLike.DISLIKE }
            cache(reactionCache(7, 43)) { SongLike.NONE }
            cache.invalidate(reactionCache.partition(7))

            store.assertSetMissing(cacheKeyReactionKey(7, SongLike.LIKE))
            store.assertSetMissing(cacheKeyReactionKey(7, SongLike.DISLIKE))
            store.assertSetMissing(cacheKeyReactionKey(7, SongLike.NONE))
        }

        test("explicit enum membership storage supports custom enum names") {
            val songId = keyPart<Int>("songId")
            val accountId = keyPart<Int>("accountId")
            val reactionCache = cacheKey("cache-key-custom-reaction", returns<SongLike>(), key = partitioned(
                    partition = songId,
                    key = accountId,
                ),
                storage = enumMembershipStorage(
                    values = listOf(SongLike.LIKE, SongLike.DISLIKE),
                    valueName = { like -> like.name.lowercase() },
                ),
            )

            cache(reactionCache(7, 42)) { SongLike.DISLIKE }

            store.assertSetMember("cache-key-custom-reaction:7:dislike", 42)
            store.assertSetMissing("cache-key-custom-reaction:7:DISLIKE")
        }

        test("power users can force indexed value storage for boolean and enum results") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val accountId = keyPart<Int>("accountId")
            val booleanCache = cacheKey("cache-key-bool-indexed", returns<Boolean>(), key = partitioned(
                    partition = artistId,
                    key = accountId,
                ),
                storage = indexedValueStorage(),
            )
            val enumCache = cacheKey("cache-key-enum-indexed", returns<SongLike>(), key = partitioned(
                    partition = songId,
                    key = accountId,
                ),
                storage = indexedValueStorage(),
            )

            cache(booleanCache(3, 7)) { true }
            cache(enumCache(3, 7)) { SongLike.LIKE }

            store.assertHashField("cache-key-bool-indexed:3", 7, "true")
            store.assertHashField("cache-key-enum-indexed:3", 7, "\"LIKE\"")
            store.assertSetMissing("cache-key-bool-indexed:3")
            store.assertSetMissing("cache-key-enum-indexed:3:${SongLike.LIKE.name}")
        }

        test("all invalidation removes every exact value without deleting similarly prefixed caches") {
            val songId = keyPart<Int>("songId")
            val exactCache = cacheKey("cache-key-all-exact", returns<CachedSong>(), key = exact(songId))
            val similarlyPrefixedCache = cacheKey("cache-key-all-exact-extra", returns<CachedSong>(), key = exact(songId))

            cache(exactCache(7)) { CachedSong(7, "Seven") }
            cache(exactCache(8)) { CachedSong(8, "Eight") }
            cache(similarlyPrefixedCache(7)) { CachedSong(70, "Still here") }

            cache.invalidate(exactCache.all())

            store.assertStringValueMissing("cache-key-all-exact:7")
            store.assertStringValueMissing("cache-key-all-exact:8")
            store.assertStringValue("cache-key-all-exact-extra:7", """{"id":70,"title":"Still here"}""")
        }

        test("all invalidation removes every indexed value across partitions") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val indexedCache = cacheKey("cache-key-all-indexed", returns<CachedSong>(), key = partitioned(partition = artistId, key = songId))
            val similarlyPrefixedCache = cacheKey("cache-key-all-indexed-extra", returns<CachedSong>(), key = partitioned(partition = artistId, key = songId))

            cache(indexedCache(3, 7)) { CachedSong(7, "Seven") }
            cache(indexedCache(4, 8)) { CachedSong(8, "Eight") }
            cache(similarlyPrefixedCache(3, 7)) { CachedSong(70, "Still here") }

            cache.invalidate(indexedCache.all())

            store.assertHashMissing("cache-key-all-indexed:3")
            store.assertHashMissing("cache-key-all-indexed:4")
            store.assertHashField("cache-key-all-indexed-extra:3", 7, """{"id":70,"title":"Still here"}""")
        }

        test("all invalidation removes every membership and enum classification set") {
            val subjectId = keyPart<Int>("subjectId")
            val accountId = keyPart<Int>("accountId")
            val followCache = cacheKey("cache-key-all-follow", returns<Boolean>(), key = partitioned(partition = subjectId, key = accountId))
            val reactionCache = cacheKey("cache-key-all-reaction", returns<SongLike>(), key = partitioned(partition = subjectId, key = accountId))

            cache(followCache(3, 7)) { true }
            cache(followCache(4, 8)) { false }
            cache(reactionCache(3, 7)) { SongLike.LIKE }
            cache(reactionCache(4, 8)) { SongLike.DISLIKE }

            cache.invalidate(followCache.all(), reactionCache.all())

            store.assertSetMissing("cache-key-all-follow:3")
            store.assertSetMissing("cache-key-all-follow:4:__kacheable_non_members")
            store.assertSetMissing("cache-key-all-reaction:3:${SongLike.LIKE.name}")
            store.assertSetMissing("cache-key-all-reaction:4:${SongLike.DISLIKE.name}")
        }

        test("matchable entry-key parts force indexed value storage for boolean results under auto") {
            val artistId = keyPart<Int>("artistId")
            val accountId = keyPart<Int>("accountId")
            val locale = matchableKeyPart<String>("locale")
            val followCache = cacheKey("cache-key-scanned-follow", returns<Boolean>(), key = partitioned(
                    partition = artistId,
                    key = accountId + locale,
                ),
            )

            cache(followCache(3, 7, "en")) { true }

            store.assertHashField("cache-key-scanned-follow:3", "7,en", "true")
            store.assertSetMissing("cache-key-scanned-follow:3")
        }
    }

    testFixture {
        BlockingCacheFixture()
    } asContextForEach {
        test("blocking cache supports exact indexed boolean and enum caches") {
            val id = keyPart<Int>("id")
            val accountId = keyPart<Int>("accountId")
            val exactCache = cacheKey("cache-key-blocking-song", returns<CachedSong>(), key = exact(id))
            val indexedCache = cacheKey("cache-key-blocking-artist-song", returns<CachedSong>(), key = partitioned(partition = id, key = accountId))
            val followCache = cacheKey("cache-key-blocking-follow", returns<Boolean>(), key = partitioned(partition = id, key = accountId))
            val reactionCache = cacheKey("cache-key-blocking-reaction", returns<SongLike>(), key = partitioned(partition = id, key = accountId))

            val exact = cache(exactCache(7)) { CachedSong(7, "Seven") }
            val indexed = cache(indexedCache(3, 7)) { CachedSong(7, "Seven") }
            val follows = cache(followCache(3, 7)) { true }
            val reaction = cache(reactionCache(3, 7)) { SongLike.LIKE }

            assertEquals(CachedSong(7, "Seven"), exact)
            assertEquals(CachedSong(7, "Seven"), indexed)
            assertEquals(true, follows)
            assertEquals(SongLike.LIKE, reaction)
            store.assertHashField("cache-key-blocking-artist-song:3", 7, """{"id":7,"title":"Seven"}""")
            store.assertSetMember("cache-key-blocking-follow:3", 7)
            store.assertSetMember("cache-key-blocking-reaction:3:${SongLike.LIKE.name}", 7)

            cache.invalidate(
                exactCache.all(),
                indexedCache.all(),
                followCache.all(),
                reactionCache.all(),
            )

            store.assertStringValueMissing("cache-key-blocking-song:7")
            store.assertHashMissing("cache-key-blocking-artist-song:3")
            store.assertSetMissing("cache-key-blocking-follow:3")
            store.assertSetMissing("cache-key-blocking-reaction:3:${SongLike.LIKE.name}")
        }
    }

    testFixture {
        SuspendCacheFixture(namingStrategy = cacheKeyNamingStrategy)
    } asContextForEach {
        test("cache keys route index and keyed scan parts through the naming strategy") {
            val artistId = keyPart<Int>("artistId")
            val songId = keyPart<Int>("songId")
            val locale = matchableKeyPart<String>("locale")
            val exactCache = cacheKey("cache-key-named-exact", returns<CachedSong>(), key = exact(songId))
            val indexedCache = cacheKey("cache-key-named-indexed", returns<CachedSong>(), key = partitioned(
                    partition = artistId,
                    key = songId + locale,
                ),
            )

            cache(exactCache(7)) { CachedSong(7, "Seven") }
            cache(indexedCache(3, 7, "en")) { CachedSong(7, "Seven") }

            store.assertStringValue("cache-key-named-exact|combined=7", """{"id":7,"title":"Seven"}""")
            store.assertHashField(
                "cache-key-named-indexed|primary=3",
                "secondary=7|en",
                """{"id":7,"title":"Seven"}""",
            )
        }
    }
}

private fun cacheKeyReactionKey(songId: Int, like: SongLike): String = "cache-key-song-reaction:$songId:${like.name}"

private val cacheKeyNamingStrategy = CacheNamingStrategy { name, primaryParams, secondaryParams ->
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
