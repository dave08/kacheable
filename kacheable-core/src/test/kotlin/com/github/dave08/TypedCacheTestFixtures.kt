@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.keyPart
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

val structuredKeyStrategy = CacheNamingStrategy { name, storage, primaryParams, secondaryParams ->
    when (storage) {
        CacheStorage.HashMap ->
            when (name) {
                "song-page-cache" ->
                    if (secondaryParams.isEmpty()) {
                        CacheEntryName.Flat("$name|song=${primaryParams[0]}")
                    } else {
                        CacheEntryName.Layered(
                            key = "$name|song=${primaryParams[0]}",
                            entry = "${secondaryParams[0]},${secondaryParams[1]},${secondaryParams[2]}",
                        )
                    }
                "song-section-cache" ->
                    if (secondaryParams.isEmpty()) {
                        CacheEntryName.Flat("$name:${primaryParams.joinToString("|")}")
                    } else {
                        CacheEntryName.Layered(
                            key = "$name:${primaryParams.joinToString("|")}",
                            entry = secondaryParams.joinToString(","),
                        )
                    }
                "wide-cache" ->
                    if (secondaryParams.isEmpty()) {
                        CacheEntryName.Flat("$name:${primaryParams.joinToString("|")}")
                    } else {
                        CacheEntryName.Layered(
                            key = "$name:${primaryParams.joinToString("|")}",
                            entry = secondaryParams.joinToString(","),
                        )
                    }
                else ->
                    if (secondaryParams.isEmpty()) {
                        CacheEntryName.Flat(if (primaryParams.isEmpty()) name else "$name:${primaryParams.joinToString(",")}")
                    } else {
                        CacheEntryName.Layered(
                            key = if (primaryParams.isEmpty()) name else "$name:${primaryParams.joinToString(",")}",
                            entry = secondaryParams.joinToString(","),
                        )
                    }
            }
        else ->
            CacheEntryName.Flat(
                combineForTests(name, ":", primaryParams, secondaryParams),
            )
    }
}

val bracketedKeyStrategy = CacheNamingStrategy { name, storage, primaryParams, secondaryParams ->
    when (storage) {
        CacheStorage.HashMap, CacheStorage.Set ->
            if (secondaryParams.isEmpty()) {
                CacheEntryName.Flat(
                    if (primaryParams.isEmpty()) name else "$name[${primaryParams.joinToString("][")}]",
                )
            } else {
                CacheEntryName.Layered(
                    key = if (primaryParams.isEmpty()) name else "$name[${primaryParams.joinToString("][")}]",
                    entry = secondaryParams.joinToString(","),
                )
            }
        else -> {
            CacheEntryName.Flat(
                combineForTests(name, "][", primaryParams, secondaryParams, prefix = "[", suffix = "]"),
            )
        }
    }
}

val verboseEntryStrategy = CacheNamingStrategy { name, storage, primaryParams, secondaryParams ->
    val primaryKey = if (primaryParams.isEmpty()) name else "$name|${primaryParams.joinToString("|")}"

    when (storage) {
        CacheStorage.HashMap, CacheStorage.Set ->
            if (secondaryParams.isEmpty()) {
                CacheEntryName.Flat(primaryKey)
            } else {
                CacheEntryName.Layered(
                    key = primaryKey,
                    entry = secondaryParams.mapIndexed { index, value -> "part$index=$value" }.joinToString("|"),
                )
            }
        else -> CacheEntryName.Flat(
            combineForTests(name, "|", primaryParams, secondaryParams),
        )
    }
}

val songIdKey = keyPart<SongId>(SongId::value)
val songSectionKey = keyPart<SongSection>({ it.id.value }, SongSection::category)
val typedPagingKey = keyPart<PageWindow>(PageWindow::offset, PageWindow::limit)
val typedLocaleKey = keyPart<String>()
val typedFilterKey = keyPart<String>()
val typedSortKey = keyPart<String>()
val typedPageSizeKey = keyPart<Int>()
val typedMarketKey = keyPart<String>()

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
