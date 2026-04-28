@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
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

val structuredKeyStrategy = CacheNamingStrategy { name, storage, mainParams, secondaryParams ->
    when (storage) {
        CacheStorage.HashMap ->
            when (name) {
                "song-page-cache" ->
                    if (secondaryParams.isEmpty()) {
                        CacheEntryName.Combined("$name|song=${mainParams[0]}")
                    } else {
                        CacheEntryName.Split(
                            key = "$name|song=${mainParams[0]}",
                            entry = "${secondaryParams[0]},${secondaryParams[1]},${secondaryParams[2]}",
                        )
                    }
                "song-section-cache" ->
                    if (secondaryParams.isEmpty()) {
                        CacheEntryName.Combined("$name:${mainParams.joinToString("|")}")
                    } else {
                        CacheEntryName.Split(
                            key = "$name:${mainParams.joinToString("|")}",
                            entry = secondaryParams.joinToString(","),
                        )
                    }
                "wide-cache" ->
                    if (secondaryParams.isEmpty()) {
                        CacheEntryName.Combined("$name:${mainParams.joinToString("|")}")
                    } else {
                        CacheEntryName.Split(
                            key = "$name:${mainParams.joinToString("|")}",
                            entry = secondaryParams.joinToString(","),
                        )
                    }
                else ->
                    if (secondaryParams.isEmpty()) {
                        CacheEntryName.Combined(if (mainParams.isEmpty()) name else "$name:${mainParams.joinToString(",")}")
                    } else {
                        CacheEntryName.Split(
                            key = if (mainParams.isEmpty()) name else "$name:${mainParams.joinToString(",")}",
                            entry = secondaryParams.joinToString(","),
                        )
                    }
            }
        else ->
            CacheEntryName.Combined(
                combineForTests(name, ":", mainParams, secondaryParams),
            )
    }
}

val bracketedKeyStrategy = CacheNamingStrategy { name, storage, mainParams, secondaryParams ->
    when (storage) {
        CacheStorage.HashMap, CacheStorage.Set ->
            if (secondaryParams.isEmpty()) {
                CacheEntryName.Combined(
                    if (mainParams.isEmpty()) name else "$name[${mainParams.joinToString("][")}]",
                )
            } else {
                CacheEntryName.Split(
                    key = if (mainParams.isEmpty()) name else "$name[${mainParams.joinToString("][")}]",
                    entry = secondaryParams.joinToString(","),
                )
            }
        else -> {
            CacheEntryName.Combined(
                combineForTests(name, "][", mainParams, secondaryParams, prefix = "[", suffix = "]"),
            )
        }
    }
}

val verboseEntryStrategy = CacheNamingStrategy { name, storage, mainParams, secondaryParams ->
    val mainKey = if (mainParams.isEmpty()) name else "$name|${mainParams.joinToString("|")}"

    when (storage) {
        CacheStorage.HashMap, CacheStorage.Set ->
            if (secondaryParams.isEmpty()) {
                CacheEntryName.Combined(mainKey)
            } else {
                CacheEntryName.Split(
                    key = mainKey,
                    entry = secondaryParams.mapIndexed { index, value -> "part$index=$value" }.joinToString("|"),
                )
            }
        else -> CacheEntryName.Combined(
            combineForTests(name, "|", mainParams, secondaryParams),
        )
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

private fun combineForTests(
    name: String,
    separator: String,
    mainParams: Array<out Any>,
    secondaryParams: Array<out Any>,
    prefix: String = separator,
    suffix: String = "",
): String {
    val params = buildList {
        addAll(mainParams.asList())
        addAll(secondaryParams.asList())
    }
    return if (params.isEmpty()) {
        name
    } else {
        "$name$prefix${params.joinToString(separator)}$suffix"
    }
}
