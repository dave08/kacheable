@file:Suppress("DEPRECATION")

package com.github.dave08

import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheEntryRef
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.argsOf
import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.defaultCacheNamingStrategy
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.matchableKeyPart
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.rawKeyPart
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private data class DelegatedParts(
    val songId: com.github.dave08.kacheable.KeyPart<Int>,
    val locale: com.github.dave08.kacheable.KeyPart<String>,
)

val CacheKeyRegressionCoverageSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("exact cache keys compose same-level key parts with plus") {
            val songId by keyPart<Int>()
            val locale = keyPart<String>("locale")
            val songCache = cacheKey("regression-song-exact", returns<TestSong>(), key = exact(songId + locale))

            cache(songCache(21, "en")) {
                TestSong(21, "Exact")
            }

            store.assertStringValue("regression-song-exact:21,en", """{"id":21,"title":"Exact"}""")

            cache.invalidate(songCache(21, "en"))

            store.assertStringValueMissing("regression-song-exact:21,en")
        }

        test("raw key parts preserve untyped key segment escape hatch") {
            val rawSongsCache = cacheKey("regression-raw-song", returns<TestSong>(), key = exact(rawKeyPart()))

            cache(rawSongsCache(argsOf(7, "en"))) {
                TestSong(7, "Raw")
            }

            store.assertStringValue("regression-raw-song:7,en", """{"id":7,"title":"Raw"}""")
        }

        test("partitioned keys compose mapped key parts and invalidate only one partition") {
            val songId = keyPart<SongId>("songId", SongId::value)
            val section = keyPart<SongSection>("section", { it.id.value }, SongSection::category)
            val sectionCache = cacheKey(
                "regression-song-section",
                returns<TestSong>(),
                key = partitioned(partition = songId, key = section),
            )

            cache(sectionCache(SongId(4), SongSection(SongId(4), "lyrics"))) {
                TestSong(4, "Lyrics")
            }
            cache(sectionCache(SongId(4), SongSection(SongId(4), "credits"))) {
                TestSong(4, "Credits")
            }
            cache(sectionCache(SongId(5), SongSection(SongId(5), "lyrics"))) {
                TestSong(5, "Other")
            }

            cache.invalidate(sectionCache.partition(SongId(4)))

            store.assertHashMissing("regression-song-section:4")
            store.assertHashField("regression-song-section:5", "5,lyrics", """{"id":5,"title":"Other"}""")
        }

        test("partitioned mapped key parts can group and invalidate image variants") {
            val imageId = keyPart<String>("imageId")
            val variant = keyPart<ImageVariantRequest>("variant", ImageVariantRequest::format, ImageVariantRequest::width)
            val imageVariantsCache = cacheKey(
                "regression-image-variants",
                returns<CachedImageVariant>(),
                key = partitioned(partition = imageId, key = variant),
            )

            cache(imageVariantsCache("cover-7", ImageVariantRequest(format = "webp", width = 320))) {
                CachedImageVariant(
                    url = "https://cdn.example.test/images/cover-7-320.webp",
                    width = 320,
                    height = 320,
                )
            }
            cache(imageVariantsCache("cover-7", ImageVariantRequest(format = "jpg", width = 1280))) {
                CachedImageVariant(
                    url = "https://cdn.example.test/images/cover-7-1280.jpg",
                    width = 1280,
                    height = 1280,
                )
            }
            cache(imageVariantsCache("cover-8", ImageVariantRequest(format = "webp", width = 320))) {
                CachedImageVariant(
                    url = "https://cdn.example.test/images/cover-8-320.webp",
                    width = 320,
                    height = 320,
                )
            }

            store.assertHashField(
                "regression-image-variants:cover-7",
                "webp,320",
                """{"url":"https://cdn.example.test/images/cover-7-320.webp","width":320,"height":320}""",
            )

            cache.invalidate(imageVariantsCache.partition("cover-7"))

            store.assertHashMissing("regression-image-variants:cover-7")
            store.assertHashField(
                "regression-image-variants:cover-8",
                "webp,320",
                """{"url":"https://cdn.example.test/images/cover-8-320.webp","width":320,"height":320}""",
            )
        }

        test("partitioned keys support one partition plus five item-key parameters") {
            val artistId = keyPart<Int>("artistId")
            val filter = keyPart<String>("filter")
            val sort = keyPart<String>("sort")
            val pageSize = keyPart<Int>("pageSize")
            val market = keyPart<String>("market")
            val locale = keyPart<String>("locale")
            val catalogCache = cacheKey(
                "regression-wide-catalog",
                returns<TestSong>(),
                key = partitioned(partition = artistId, key = filter + sort + pageSize + market + locale),
            )

            cache(catalogCache(3, "favorites", "recent", 25, "us", "en")) {
                TestSong(7, "Wide")
            }
            cache(catalogCache(4, "favorites", "recent", 25, "us", "en")) {
                TestSong(8, "Other")
            }

            store.assertHashField(
                "regression-wide-catalog:3",
                "favorites,recent,25,us,en",
                """{"id":7,"title":"Wide"}""",
            )

            cache.invalidate(catalogCache.partition(3))

            store.assertHashMissing("regression-wide-catalog:3")
            store.assertHashField(
                "regression-wide-catalog:4",
                "favorites,recent,25,us,en",
                """{"id":8,"title":"Other"}""",
            )
        }

        test("cacheIf can skip saving a cache-key result") {
            val songId = keyPart<Int>("songId")
            val songCache = cacheKey("regression-cache-if", returns<TestSong>(), key = exact(songId))

            val result = cache(songCache(9), cacheIf = { false }) {
                TestSong(9, "Live")
            }

            assertEquals(TestSong(9, "Live"), result)
            store.assertStringValueMissing("regression-cache-if:9")
        }

        test("typed cache calls work from classes that delegate Kacheable") {
            val songId = keyPart<Int>("songId")
            val songCache = cacheKey("regression-delegated-runtime", returns<TestSong>(), key = exact(songId))
            val repository = DelegatingSuspendRepository(cache)

            val first = repository.getSong(songCache(11)) {
                TestSong(11, "Delegated Runtime")
            }
            val second = repository.getSong(songCache(11)) {
                error("cached value should be returned")
            }

            assertEquals(TestSong(11, "Delegated Runtime"), first)
            assertEquals(first, second)

            repository.clearSong(songCache(11))

            store.assertStringValueMissing("regression-delegated-runtime:11")
        }

        test("matchable key parts can be selected by name without reusing the original instance") {
            val artistId = keyPart<Int>("artistId")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val locale = matchableKeyPart<String>("locale")
            val pagesCache = cacheKey(
                "regression-named-matchable",
                returns<List<CachedSong>>(),
                key = partitioned(partition = artistId, key = page + locale),
            )

            cache(pagesCache(7, ResultPage(0, 10), "en")) { listOf(CachedSong(7, "EN")) }
            cache(pagesCache(7, ResultPage(0, 10), "he")) { listOf(CachedSong(8, "HE")) }

            cache.invalidate(pagesCache.matching(7, matchableKeyPart<String>("locale")("en")))

            assertNull(store.hashMap["regression-named-matchable:7"]?.get("0,10,en"))
            store.assertHashField("regression-named-matchable:7", "0,10,he", """[{"id":8,"title":"HE"}]""")
        }

        test("matching rejects selectors that are not declared on the cache key") {
            val artistId = keyPart<Int>("artistId")
            val locale = matchableKeyPart<String>("locale")
            val device = matchableKeyPart<String>("device")
            val cacheKey = cacheKey(
                "regression-matchable-guard",
                returns<TestSong>(),
                key = partitioned(partition = artistId, key = locale),
            )

            assertFailsWith<IllegalArgumentException> {
                cacheKey.matching(7, device("mobile"))
            }
        }

        test("duplicate named key parts are rejected for exact and partitioned shapes") {
            assertFailsWith<IllegalArgumentException> {
                val exactCache = cacheKey(
                    "regression-duplicate-exact",
                    returns<TestSong>(),
                    key = exact(keyPart<Int>("songId") + keyPart<String>("songId")),
                )
                exactCache(7, "en")
            }
            assertFailsWith<IllegalArgumentException> {
                val partitionedCache = cacheKey(
                    "regression-duplicate-partitioned",
                    returns<TestSong>(),
                    key = partitioned(
                        partition = keyPart<Int>("artistId"),
                        key = keyPart<String>("locale") + keyPart<String>("locale"),
                    ),
                )
                partitionedCache(7, "en", "he")
            }
        }

        test("delegated key parts pick up property names and participate in duplicate guards") {
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
            val composedCache = cacheKey(
                "regression-delegated-composed",
                returns<TestSong>(),
                key = exact(delegated.songId + delegated.locale),
            )

            assertFailsWith<IllegalArgumentException> {
                val duplicateCache = cacheKey(
                    "regression-delegated-duplicate",
                    returns<TestSong>(),
                    key = exact(delegated.songId + keyPart<Int>("songId")),
                )
                duplicateCache(7, 8)
            }

            cache(composedCache(7, "he")) {
                TestSong(7, "Delegated")
            }

            store.assertStringValue("regression-delegated-composed:7,he", """{"id":7,"title":"Delegated"}""")
        }
    }

    testFixture {
        SuspendCacheFixture(namingStrategy = bracketedKeyStrategy)
    } asContextForEach {
        test("raw cache calls use the configured key naming strategy") {
            val result = cache<TestSong>("regression-raw-named", 7, "en") {
                TestSong(7, "Raw Named")
            }

            assertEquals(TestSong(7, "Raw Named"), result)
            store.assertStringValue("regression-raw-named[7][en]", """{"id":7,"title":"Raw Named"}""")
        }

        test("custom naming strategy applies to exact cache keys") {
            val songId = keyPart<Int>("songId")
            val locale = keyPart<String>("locale")
            val songCache = cacheKey("regression-named-exact", returns<TestSong>(), key = exact(songId + locale))

            cache(songCache(7, "en")) {
                TestSong(7, "Named")
            }

            store.assertStringValue("regression-named-exact[7][en]", """{"id":7,"title":"Named"}""")
        }

        test("custom naming strategy applies to partitioned hash fields and partition invalidation") {
            val artistId = keyPart<Int>("artistId")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val locale = matchableKeyPart<String>("locale")
            val pageCache = cacheKey(
                "regression-named-pages",
                returns<TestSong>(),
                key = partitioned(partition = artistId, key = page + locale),
            )

            cache(pageCache(7, ResultPage(0, 10), "en")) {
                TestSong(7, "Page 1")
            }
            cache(pageCache(7, ResultPage(10, 10), "en")) {
                TestSong(7, "Page 2")
            }

            assertEquals("""{"id":7,"title":"Page 1"}""", store.getHashValue("regression-named-pages[7]", "0,10,en"))

            cache.invalidate(pageCache.partition(7))

            store.assertHashMissing("regression-named-pages[7]")
        }

        test("custom naming strategy applies to membership and enum classification sets") {
            val artistId = keyPart<Int>("artistId")
            val accountId = keyPart<Int>("accountId")
            val followCache = cacheKey(
                "regression-named-follow",
                returns<Boolean>(),
                key = partitioned(partition = artistId, key = accountId),
            )
            val reactionCache = cacheKey(
                "regression-named-reaction",
                returns<SongLike>(),
                key = partitioned(partition = artistId, key = accountId),
            )

            cache(followCache(3, 7)) { false }
            cache(reactionCache(3, 7)) { SongLike.DISLIKE }

            store.assertSetMember("regression-named-follow[3]:__kacheable_non_members", 7)
            store.assertSetMember("regression-named-reaction[3]:DISLIKE", 7)
            store.assertSetMissing("regression-named-reaction[3]:LIKE")
        }
    }

    testFixture {
        SuspendCacheFixture(namingStrategy = verboseEntryStrategy)
    } asContextForEach {
        test("custom naming strategies can define secondary entry formatting") {
            val artistId = keyPart<Int>("artistId")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val locale = keyPart<String>("locale")
            val pageCache = cacheKey(
                "regression-verbose-page",
                returns<TestSong>(),
                key = partitioned(partition = artistId, key = page + locale),
            )

            cache(pageCache(7, ResultPage(0, 10), "en")) {
                TestSong(7, "Verbose")
            }

            assertEquals(
                """{"id":7,"title":"Verbose"}""",
                store.getHashValue("regression-verbose-page|7", "part0=0|part1=10|part2=en"),
            )
        }
    }

    testFixture {
        SuspendCacheFixture(
            namingStrategy = defaultCacheNamingStrategy(
                secondaryEntryCombiner = { params -> params.joinToString("|") },
            ),
        )
    } asContextForEach {
        test("default naming strategy can customize only the layered entry combiner") {
            val artistId = keyPart<Int>("artistId")
            val page = keyPart<ResultPage>("page", ResultPage::offset, ResultPage::limit)
            val locale = keyPart<String>("locale")
            val pageCache = cacheKey(
                "regression-default-combiner",
                returns<TestSong>(),
                key = partitioned(partition = artistId, key = page + locale),
            )

            cache(pageCache(7, ResultPage(0, 10), "en")) {
                TestSong(7, "Default Combiner")
            }

            assertEquals(
                """{"id":7,"title":"Default Combiner"}""",
                store.getHashValue("regression-default-combiner:7", "0|10|en"),
            )
        }
    }

    test("deprecated GetNameStrategy factory still routes raw cache naming through the new strategy") {
        val store = InMemoryKacheableStore()
        val cache = Kacheable(
            store = store,
            getNameStrategy = GetNameStrategy { name, params ->
                if (params.isEmpty()) name else "$name<${params.joinToString("|")}>"
            },
        )

        val result = cache<TestSong>("regression-get-name-strategy", 7, "en") {
            TestSong(7, "Deprecated")
        }

        assertEquals(TestSong(7, "Deprecated"), result)
        assertEquals("""{"id":7,"title":"Deprecated"}""", store.get("regression-get-name-strategy<7|en>"))
    }

    testFixture {
        BlockingCacheFixture(namingStrategy = bracketedKeyStrategy)
    } asContextForEach {
        test("blocking cache keys preserve composed exact and partitioned invalidation behavior") {
            val songId = keyPart<Int>("songId")
            val locale = keyPart<String>("locale")
            val artistId = keyPart<Int>("artistId")
            val exactCache = cacheKey("regression-blocking-exact", returns<TestSong>(), key = exact(songId + locale))
            val pageCache = cacheKey("regression-blocking-pages", returns<TestSong>(), key = partitioned(partition = artistId, key = songId + locale))

            cache(exactCache(7, "en")) {
                TestSong(7, "Exact")
            }
            cache(pageCache(3, 7, "en")) {
                TestSong(7, "Page")
            }

            assertEquals("""{"id":7,"title":"Exact"}""", store.get("regression-blocking-exact[7][en]"))
            store.assertHashField("regression-blocking-pages[3]", "7,en", """{"id":7,"title":"Page"}""")

            cache.invalidate(exactCache(7, "en"), pageCache.partition(3))

            store.assertStringValueMissing("regression-blocking-exact[7][en]")
            store.assertHashMissing("regression-blocking-pages[3]")
        }

        test("blocking typed cache calls work from classes that delegate BlockingKacheable") {
            val songId = keyPart<Int>("songId")
            val songCache = cacheKey("regression-blocking-delegated-runtime", returns<TestSong>(), key = exact(songId))
            val repository = DelegatingBlockingRepository(cache)

            val first = repository.getSong(songCache(12)) {
                TestSong(12, "Blocking Delegated Runtime")
            }
            val second = repository.getSong(songCache(12)) {
                error("cached value should be returned")
            }

            assertEquals(TestSong(12, "Blocking Delegated Runtime"), first)
            assertEquals(first, second)

            repository.clearSong(songCache(12))

            store.assertStringValueMissing("regression-blocking-delegated-runtime[12]")
        }
    }
}

private class DelegatingSuspendRepository(
    cache: Kacheable,
) : Kacheable by cache {
    suspend fun getSong(ref: CacheEntryRef<TestSong>, block: suspend () -> TestSong): TestSong =
        invoke(ref, block = block)

    suspend fun clearSong(ref: CacheEntryRef<TestSong>) {
        invalidate(ref)
    }
}

private class DelegatingBlockingRepository(
    cache: BlockingKacheable,
) : BlockingKacheable by cache {
    fun getSong(ref: CacheEntryRef<TestSong>, block: () -> TestSong): TestSong =
        invoke(ref, block = block)

    fun clearSong(ref: CacheEntryRef<TestSong>) {
        invalidate(ref)
    }
}

private data class SongId(val value: Int)

private data class SongSection(
    val id: SongId,
    val category: String,
)

private val bracketedKeyStrategy = CacheNamingStrategy { name, primaryParams, secondaryParams ->
    val primary = if (primaryParams.isEmpty()) {
        name
    } else {
        "$name${primaryParams.joinToString(separator = "") { "[$it]" }}"
    }
    if (secondaryParams.isEmpty()) {
        CacheEntryName.Primary(primary = primary, combined = primary)
    } else {
        CacheEntryName.PrimarySecondary(
            primary = primary,
            secondary = secondaryParams.joinToString(","),
            combine = { _, _ ->
                "$name${(primaryParams.asList() + secondaryParams.asList()).joinToString(separator = "") { "[$it]" }}"
            },
        )
    }
}

private val verboseEntryStrategy = CacheNamingStrategy { name, primaryParams, secondaryParams ->
    if (secondaryParams.isEmpty()) {
        val primary = if (primaryParams.isEmpty()) name else "$name|${primaryParams.joinToString("|")}"
        CacheEntryName.Primary(primary = primary, combined = primary)
    } else {
        CacheEntryName.PrimarySecondary(
            primary = "$name|${primaryParams.joinToString("|")}",
            secondary = secondaryParams.withIndex().joinToString("|") { (index, value) -> "part$index=$value" },
            combine = { _, _ -> "$name|${(primaryParams.asList() + secondaryParams.asList()).joinToString("|")}" },
        )
    }
}
