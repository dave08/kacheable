@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

private fun <P1> mappedKeyPart(
    name: String? = null,
    values: List<(P1) -> Any?>,
): KeyPart<P1> {
    require(values.isNotEmpty()) { "keyPart requires at least one value extractor" }
    return SimpleKeyPart(
        name = name,
        encoders = values,
    )
}

/**
 * Creates an unnamed key part that uses the value itself as the cache-key segment.
 *
 * Example:
 *
 * ```kotlin
 * val page = keyPart<Int>()
 * val pages = cacheKey("pages", returns<Page>(), key = exact(page))
 * cache(pages(3)) { loadPage(3) }
 * ```
 */
@ExperimentalKacheableApi
fun <P1> keyPart(): KeyPart<P1> = mappedKeyPart(values = listOf({ it }))

/**
 * Creates an unnamed key part from one or more extracted segments.
 *
 * Example:
 *
 * ```kotlin
 * data class Page(val offset: Int, val limit: Int)
 *
 * val page = keyPart<Page>(Page::offset, Page::limit)
 * ```
 */
@ExperimentalKacheableApi
fun <P1> keyPart(
    vararg values: (P1) -> Any?,
): KeyPart<P1> = mappedKeyPart(values = values.toList())

/**
 * Creates a named key part that uses the value itself as the cache-key segment.
 *
 * Prefer named key parts for typed cache keys so custom naming strategies and matching
 * invalidations can identify each part.
 *
 * Example:
 *
 * ```kotlin
 * val songId = keyPart<Int>("songId")
 * val songCache = cacheKey("song", returns<Song>(), key = exact(songId))
 * ```
 */
@ExperimentalKacheableApi
fun <P1> keyPart(
    name: String,
): KeyPart<P1> = mappedKeyPart(name = name, values = listOf({ it }))

/**
 * Creates a named key part from one or more extracted segments.
 *
 * Example:
 *
 * ```kotlin
 * val page = keyPart<Page>("page", Page::offset, Page::limit)
 * ```
 */
@ExperimentalKacheableApi
fun <P1> keyPart(
    name: String,
    vararg values: (P1) -> Any?,
): KeyPart<P1> = mappedKeyPart(name = name, values = values.toList())

/**
 * Escape hatch for already-encoded cache arguments.
 *
 * Raw key parts cannot be used with matching invalidations because their segment count is not
 * known to the type-safe key API.
 */
@ExperimentalKacheableApi
fun rawKeyPart(): KeyPart<CacheArgs> = RawKeyPart
