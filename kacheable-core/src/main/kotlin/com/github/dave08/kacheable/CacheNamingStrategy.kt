@file:Suppress("DEPRECATION")

package com.github.dave08.kacheable

/**
 * Resolved cache names for a storage operation.
 */
sealed interface CacheEntryName {
    data class Primary(
        val primary: String,
        val combined: String = primary,
    ) : CacheEntryName

    data class PrimarySecondary(
        val primary: String,
        val secondary: String,
        val combine: (String, String) -> String,
    ) : CacheEntryName {
        val combined: String
            get() = combine(primary, secondary)
    }
}

val CacheEntryName.primaryKey: String
    get() =
        when (this) {
            is CacheEntryName.Primary -> primary
            is CacheEntryName.PrimarySecondary -> primary
        }

val CacheEntryName.combinedKey: String
    get() =
        when (this) {
            is CacheEntryName.Primary -> combined
            is CacheEntryName.PrimarySecondary -> combined
        }

val CacheEntryName.secondaryEntryOrNull: String?
    get() =
        when (this) {
            is CacheEntryName.Primary -> null
            is CacheEntryName.PrimarySecondary -> secondary
        }

fun CacheEntryName.requireSecondaryEntry(): String =
    requireNotNull(secondaryEntryOrNull) { "Primary-secondary cache entries require a secondary entry name." }

fun CacheEntryName.withInternalSuffix(suffix: String): String = "$primaryKey:$suffix"

fun CacheEntryName.asCombined(): CacheEntryName.Primary = CacheEntryName.Primary(primaryKey, combinedKey)

/**
 * Naming hook used by all cache storages.
 *
 * Exact caches receive all key values as [primaryParams]. Partitioned caches receive partition
 * values as [primaryParams] and entry-key values as [secondaryParams].
 */
fun interface CacheNamingStrategy {
    fun getEntryName(
        cacheName: String,
        primaryParams: Array<out Any?>,
        secondaryParams: Array<out Any?>,
    ): CacheEntryName
}

/**
 * Default naming strategy.
 *
 * [nullKeyPart] controls how null key segments are represented in generated Redis/string keys.
 */
fun defaultCacheNamingStrategy(
    nullKeyPart: String = "<null>",
    flatKeyCombiner: (cacheName: String, params: Array<out Any?>) -> String = { cacheName, params ->
        combineCacheKey(cacheName, params, nullKeyPart)
    },
    primaryKeyCombiner: (cacheName: String, params: Array<out Any?>) -> String = { cacheName, params ->
        combineCacheKey(cacheName, params, nullKeyPart)
    },
    secondaryEntryCombiner: (params: Array<out Any?>) -> String = { params ->
        combineSecondaryEntryParts(params, nullKeyPart)
    },
): CacheNamingStrategy = CacheNamingStrategy { cacheName, primaryParams, secondaryParams ->
    if (secondaryParams.isEmpty()) {
        CacheEntryName.Primary(
            primary = primaryKeyCombiner(cacheName, primaryParams),
            combined = flatKeyCombiner(cacheName, primaryParams),
        )
    } else {
        val primaryKey = primaryKeyCombiner(cacheName, primaryParams)
        val secondaryEntry = secondaryEntryCombiner(secondaryParams)
        CacheEntryName.PrimarySecondary(
            primary = primaryKey,
            secondary = secondaryEntry,
            combine = { _, _ -> flatKeyCombiner(cacheName, combineParams(primaryParams, secondaryParams)) },
        )
    }
}

@Deprecated(
    message = "Use CacheNamingStrategy instead.",
    replaceWith = ReplaceWith("CacheNamingStrategy"),
)
fun interface GetNameStrategy {
    fun getName(name: String, params: Array<out Any?>): String
}

@Deprecated(
    message = "Use defaultCacheNamingStrategy() instead.",
    replaceWith = ReplaceWith("defaultCacheNamingStrategy()", imports = ["com.github.dave08.kacheable.defaultCacheNamingStrategy"]),
)
val DefaultGetNameStrategy: GetNameStrategy = GetNameStrategy { name, params ->
    combineCacheKey(name, params)
}

fun GetNameStrategy.asCacheNamingStrategy(): CacheNamingStrategy =
    CacheNamingStrategy { cacheName, primaryParams, secondaryParams ->
        if (secondaryParams.isEmpty()) {
            val primaryName = getName(cacheName, primaryParams)
            CacheEntryName.Primary(primaryName, primaryName)
        } else {
            CacheEntryName.PrimarySecondary(
                primary = getName(cacheName, primaryParams),
                secondary = combineSecondaryEntryParts(secondaryParams),
                combine = { _, _ -> getName(cacheName, combineParams(primaryParams, secondaryParams)) },
            )
        }
    }

/**
 * Combines a cache name and encoded key segments using the default null placeholder.
 */
fun combineCacheKey(name: String, params: Array<out Any?>): String =
    combineCacheKey(name, params, nullKeyPart = "<null>")

/**
 * Combines a cache name and encoded key segments.
 */
fun combineCacheKey(
    name: String,
    params: Array<out Any?>,
    nullKeyPart: String,
): String =
    if (params.isEmpty()) {
        name
    } else {
        "$name:${params.joinToString(",") { it.toCacheKeySegmentString(nullKeyPart) }}"
    }

/**
 * Combines entry-key segments for partitioned storage using the default null placeholder.
 */
fun combineSecondaryEntryParts(params: Array<out Any?>): String =
    combineSecondaryEntryParts(params, nullKeyPart = "<null>")

/**
 * Combines entry-key segments for partitioned storage.
 */
fun combineSecondaryEntryParts(
    params: Array<out Any?>,
    nullKeyPart: String,
): String = params.joinToString(",") { it.toCacheKeySegmentString(nullKeyPart) }

private fun combineParams(
    primaryParams: Array<out Any?>,
    secondaryParams: Array<out Any?>,
): Array<out Any?> = buildList {
    addAll(primaryParams.asList())
    addAll(secondaryParams.asList())
}.toTypedArray()

private fun Any?.toCacheKeySegmentString(nullKeyPart: String): String =
    this?.toString() ?: nullKeyPart
