@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.key
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.Serializable

@Serializable
internal data class CachedSong(val id: Int, val title: String)

internal data class ResultPage(val offset: Int, val limit: Int)

@Serializable
internal data class CachedImageVariant(val url: String, val width: Int, val height: Int)

internal data class ImageVariantRequest(val format: String, val width: Int)

internal val artistCache = mainKey<Int>("artist-cache", storedAs = CacheStorage.HashMap)
internal val imageCache = mainKey<String>("image-cache", storedAs = CacheStorage.HashMap)
internal val songKey = key<Int>()
internal val pageKey = key<ResultPage>(ResultPage::offset, ResultPage::limit)
internal val imageVariantKey = key<ImageVariantRequest>(ImageVariantRequest::format, ImageVariantRequest::width)
internal val filterKey = key<String>()
internal val sortKey = key<String>()
internal val pageSizeKey = key<Int>()
internal val marketKey = key<String>()
internal val localeKey = key<String>()
internal val artistSongsCache = artistCache + songKey
internal val artistPageCache = artistCache + (pageKey + localeKey)
internal val artistCatalogCache = artistCache + (filterKey + sortKey + pageSizeKey + marketKey + localeKey)
internal val imageVariantsCache = imageCache + imageVariantKey

internal fun artistCacheKey(artistId: Int): String = "artist-cache:$artistId"
internal fun imageCacheKey(imageId: String): String = "image-cache:$imageId"

internal fun artistSongFlatKey(artistId: Int, songId: Int): String = "artist-cache:$artistId,$songId"

internal fun artistCatalogField(
    filter: String,
    sort: String,
    pageSize: Int,
    market: String,
    locale: String,
): String = "$filter,$sort,$pageSize,$market,$locale"

internal fun InMemoryKacheableStore.assertHashField(
    key: String,
    field: Any,
    expectedValue: String,
) {
    assertEquals(expectedValue, hashMap[key]?.get(field.toString()))
}

internal fun InMemoryKacheableStore.assertHashMissing(key: String) {
    assertNull(hashMap[key])
}

internal suspend fun InMemoryKacheableStore.assertStringValue(
    key: String,
    expectedValue: String,
) {
    assertEquals(expectedValue, get(key))
}

internal suspend fun InMemoryKacheableStore.assertStringValueMissing(key: String) {
    assertNull(get(key))
}

internal fun InMemoryBlockingKacheableStore.assertHashField(
    key: String,
    field: Any,
    expectedValue: String,
) {
    assertEquals(expectedValue, hashMap[key]?.get(field.toString()))
}
