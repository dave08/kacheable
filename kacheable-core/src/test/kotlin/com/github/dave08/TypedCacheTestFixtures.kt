@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.key
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.plus
import kotlinx.serialization.Serializable

@Serializable
data class TestSong(val id: Int, val title: String)

data class PageWindow(
    val offset: Int,
    val limit: Int,
)

data class SongId(val value: Int)

data class SongSection(
    val id: SongId,
    val category: String,
)

val structuredKeyStrategy = GetNameStrategy { name, params ->
    when (name) {
        "song-page-cache" -> if (params.size == 1) "$name|song=${params[0]}" else
            "$name|song=${params[0]}|page=${params[1]}|limit=${params[2]}|locale=${params[3]}"
        "song-section-cache" -> "$name:${params.joinToString("|")}"
        "wide-cache" -> "$name:${params.joinToString("|")}"
        else -> if (params.isEmpty()) name else "$name:${params.joinToString(",")}"
    }
}

val songIdKey = key<SongId>(SongId::value)
val songSectionKey = key<SongSection>({ it.id.value }, SongSection::category)
val typedPagingKey = key<PageWindow>(PageWindow::offset, PageWindow::limit)
val typedLocaleKey = key<String>()
val typedFilterKey = key<String>()
val typedSortKey = key<String>()
val typedPageSizeKey = key<Int>()
val typedMarketKey = key<String>()

val typedSongPageCache = mainKey<Int>("song-page-cache", storedAs = CacheStorage.HashMap) + (typedPagingKey + typedLocaleKey)
val typedSongSectionCache = mainKey("song-section-cache", songIdKey, storedAs = CacheStorage.HashMap) + songSectionKey
val typedWideSongCache = mainKey<Int>("wide-cache", storedAs = CacheStorage.HashMap) +
    (typedFilterKey + typedSortKey + typedPageSizeKey + typedMarketKey + typedLocaleKey)

fun CacheArgs.toList(): List<Any> = toParamsArray().toList()
