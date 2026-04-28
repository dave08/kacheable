@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val StoredCacheValueSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("cache can use value return descriptors for hash fields") {
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
            store.assertStringValueMissing(artistSongFlatKey(artistId, songId))
        }

        test("stored cache key parts can invalidate entries cached with return descriptors") {
            val artistId = 3
            val firstSongId = 7
            val secondSongId = 8

            cache(artistSongsCache.key(artistId, firstSongId), returnsAs = value<CachedSong>()) {
                CachedSong(firstSongId, "Seven")
            }
            cache(artistSongsCache.key(artistId, secondSongId), returnsAs = value<CachedSong>()) {
                CachedSong(secondSongId, "Eight")
            }

            cache.invalidate(artistSongsCache.keyPart(artistId))

            store.assertHashMissing(artistCacheKey(artistId))
        }

        test("hash map stored caches support composed secondary key parts") {
            val artistId = 3
            val page = ResultPage(offset = 20, limit = 10)
            val locale = "en"

            val result = cache(artistPageCache.key(artistId, page, locale), returnsAs = value<List<CachedSong>>()) {
                listOf(CachedSong(7, "Seven"))
            }

            assertEquals(listOf(CachedSong(7, "Seven")), result)
            store.assertHashField(
                artistCacheKey(artistId),
                "20,10,en",
                """[{"id":7,"title":"Seven"}]""",
            )
        }

        test("value return descriptors can store set values") {
            val artistId = 3
            val page = ResultPage(offset = 20, limit = 10)
            val locale = "en"

            val result = cache(artistPageCache.key(artistId, page, locale), returnsAs = value<Set<Int>>()) {
                setOf(7, 8, 9)
            }

            assertEquals(setOf(7, 8, 9), result)
            store.assertHashField(artistCacheKey(artistId), "20,10,en", """[7,8,9]""")
        }

        test("hash map stored caches support the maximum cache arity") {
            val artistId = 3
            val filter = "favorites"
            val sort = "recent"
            val pageSize = 25
            val market = "us"
            val locale = "en"
            val songId = 7

            val result = cache(
                artistCatalogCache.key(artistId, filter, sort, pageSize, market, locale),
                returnsAs = value<CachedSong>(),
            ) {
                CachedSong(songId, "Seven")
            }

            assertEquals(CachedSong(songId, "Seven"), result)
            store.assertHashField(
                artistCacheKey(artistId),
                artistCatalogField(filter, sort, pageSize, market, locale),
                """{"id":7,"title":"Seven"}""",
            )
        }

        test("hash map stored caches can group multiple image variants under one source image key") {
            val imageId = "cover-7"
            val thumbnail = ImageVariantRequest(format = "webp", width = 320)
            val fullSize = ImageVariantRequest(format = "jpg", width = 1280)

            cache(imageVariantsCache.key(imageId, thumbnail), returnsAs = value<CachedImageVariant>()) {
                CachedImageVariant(
                    url = "https://cdn.example.test/images/cover-7-320.webp",
                    width = 320,
                    height = 320,
                )
            }
            cache(imageVariantsCache.key(imageId, fullSize), returnsAs = value<CachedImageVariant>()) {
                CachedImageVariant(
                    url = "https://cdn.example.test/images/cover-7-1280.jpg",
                    width = 1280,
                    height = 1280,
                )
            }

            store.assertHashField(
                imageCacheKey(imageId),
                "webp,320",
                """{"url":"https://cdn.example.test/images/cover-7-320.webp","width":320,"height":320}""",
            )
            store.assertHashField(
                imageCacheKey(imageId),
                "jpg,1280",
                """{"url":"https://cdn.example.test/images/cover-7-1280.jpg","width":1280,"height":1280}""",
            )
        }

        test("image variant caches can invalidate all variants for one source image") {
            val imageId = "cover-8"
            val otherImageId = "cover-9"

            cache(
                imageVariantsCache.key(imageId, ImageVariantRequest(format = "webp", width = 320)),
                returnsAs = value<CachedImageVariant>(),
            ) {
                CachedImageVariant(
                    url = "https://cdn.example.test/images/cover-8-320.webp",
                    width = 320,
                    height = 320,
                )
            }
            cache(
                imageVariantsCache.key(imageId, ImageVariantRequest(format = "jpg", width = 1280)),
                returnsAs = value<CachedImageVariant>(),
            ) {
                CachedImageVariant(
                    url = "https://cdn.example.test/images/cover-8-1280.jpg",
                    width = 1280,
                    height = 1280,
                )
            }
            cache(
                imageVariantsCache.key(otherImageId, ImageVariantRequest(format = "webp", width = 320)),
                returnsAs = value<CachedImageVariant>(),
            ) {
                CachedImageVariant(
                    url = "https://cdn.example.test/images/cover-9-320.webp",
                    width = 320,
                    height = 320,
                )
            }

            cache.invalidate(imageVariantsCache.keyPart(imageId))

            store.assertHashMissing(imageCacheKey(imageId))
            store.assertHashField(
                imageCacheKey(otherImageId),
                "webp,320",
                """{"url":"https://cdn.example.test/images/cover-9-320.webp","width":320,"height":320}""",
            )
        }
    }
}
