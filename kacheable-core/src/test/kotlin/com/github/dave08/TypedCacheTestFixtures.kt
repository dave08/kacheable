@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.entryKey
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.times
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

val structuredKeyStrategy = CacheNamingStrategy { name, primaryParams, secondaryParams ->
    when (name) {
        "song-page-cache" ->
            if (secondaryParams.isEmpty()) {
                CacheEntryName.Primary(
                    primary = "$name|song=${primaryParams[0]}",
                    combined = combineForTests(name, ":", primaryParams, secondaryParams),
                )
            } else {
                CacheEntryName.PrimarySecondary(
                    primary = "$name|song=${primaryParams[0]}",
                    secondary = "${secondaryParams[0]},${secondaryParams[1]},${secondaryParams[2]}",
                    combine = { _, _ -> combineForTests(name, ":", primaryParams, secondaryParams) },
                )
            }
        "song-section-cache" ->
            if (secondaryParams.isEmpty()) {
                CacheEntryName.Primary(
                    primary = "$name:${primaryParams.joinToString("|")}",
                    combined = combineForTests(name, ":", primaryParams, secondaryParams),
                )
            } else {
                CacheEntryName.PrimarySecondary(
                    primary = "$name:${primaryParams.joinToString("|")}",
                    secondary = secondaryParams.joinToString(","),
                    combine = { _, _ -> combineForTests(name, ":", primaryParams, secondaryParams) },
                )
            }
        "wide-cache" ->
            if (secondaryParams.isEmpty()) {
                CacheEntryName.Primary(
                    primary = "$name:${primaryParams.joinToString("|")}",
                    combined = combineForTests(name, ":", primaryParams, secondaryParams),
                )
            } else {
                CacheEntryName.PrimarySecondary(
                    primary = "$name:${primaryParams.joinToString("|")}",
                    secondary = secondaryParams.joinToString(","),
                    combine = { _, _ -> combineForTests(name, ":", primaryParams, secondaryParams) },
                )
            }
        else ->
            if (secondaryParams.isEmpty()) {
                CacheEntryName.Primary(
                    primary = if (primaryParams.isEmpty()) name else "$name:${primaryParams.joinToString(",")}",
                    combined = combineForTests(name, ":", primaryParams, secondaryParams),
                )
            } else {
                CacheEntryName.PrimarySecondary(
                    primary = if (primaryParams.isEmpty()) name else "$name:${primaryParams.joinToString(",")}",
                    secondary = secondaryParams.joinToString(","),
                    combine = { _, _ -> combineForTests(name, ":", primaryParams, secondaryParams) },
                )
            }
    }
}

val bracketedKeyStrategy = CacheNamingStrategy { name, primaryParams, secondaryParams ->
    val primary = if (primaryParams.isEmpty()) name else "$name[${primaryParams.joinToString("][")}]"
    if (secondaryParams.isEmpty()) {
        CacheEntryName.Primary(
            primary = primary,
            combined = combineForTests(name, "][", primaryParams, secondaryParams, prefix = "[", suffix = "]"),
        )
    } else {
        CacheEntryName.PrimarySecondary(
            primary = primary,
            secondary = secondaryParams.joinToString(","),
            combine = { _, _ -> combineForTests(name, "][", primaryParams, secondaryParams, prefix = "[", suffix = "]") },
        )
    }
}

val verboseEntryStrategy = CacheNamingStrategy { name, primaryParams, secondaryParams ->
    val primaryKey = if (primaryParams.isEmpty()) name else "$name|${primaryParams.joinToString("|")}"

    if (secondaryParams.isEmpty()) {
        CacheEntryName.Primary(
            primary = primaryKey,
            combined = combineForTests(name, "|", primaryParams, secondaryParams),
        )
    } else {
        CacheEntryName.PrimarySecondary(
            primary = primaryKey,
            secondary = secondaryParams.mapIndexed { index, value -> "part$index=$value" }.joinToString("|"),
            combine = { _, _ -> combineForTests(name, "|", primaryParams, secondaryParams) },
        )
    }
}

val storageAwareFixtureStrategy = CacheNamingStrategy { name, primaryParams, secondaryParams ->
    if (secondaryParams.isEmpty()) {
        CacheEntryName.Primary(
            primary = if (primaryParams.isEmpty()) name else "$name:${primaryParams.joinToString(",")}",
            combined = combineForTests(name, ",", primaryParams, secondaryParams, prefix = ":"),
        )
    } else {
        CacheEntryName.PrimarySecondary(
            primary = if (primaryParams.isEmpty()) name else "$name:${primaryParams.joinToString(",")}",
            secondary = secondaryParams.joinToString(","),
            combine = { _, _ -> combineForTests(name, ",", primaryParams, secondaryParams, prefix = ":") },
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

val typedSongPagePrimary = keyPart<Int>()
val typedSongPageCache = entryKey("song-page-cache", typedSongPagePrimary * (typedPagingKey + typedLocaleKey), storedAs = CacheStorage.HashMap)
val typedSongSectionCache = entryKey("song-section-cache", songIdKey * songSectionKey, storedAs = CacheStorage.HashMap)
val typedWidePrimary = keyPart<Int>()
val typedWideSongCache = entryKey(
    "wide-cache",
    typedWidePrimary * (typedFilterKey + typedSortKey + typedPageSizeKey + typedMarketKey + typedLocaleKey),
    storedAs = CacheStorage.HashMap,
)
val namedPagingKey = keyPart<PageWindow>("paging", PageWindow::offset, PageWindow::limit)
val namedLocaleKey = keyPart<String>("locale")
val namedSongPagePrimary = keyPart<Int>("artist")
val namedSongPageCache = entryKey(
    "named-song-page-cache",
    namedSongPagePrimary * (namedPagingKey + namedLocaleKey),
    storedAs = CacheStorage.HashMap,
)

fun CacheArgs.toList(): List<Any?> = toParamsArray().toList()

private fun combineForTests(
    name: String,
    separator: String,
    mainParams: Array<out Any?>,
    secondaryParams: Array<out Any?>,
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
