package com.github.dave08.kacheable

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

fun interface CacheNamingStrategy {
    fun getEntryName(
        cacheName: String,
        primaryParams: Array<out Any?>,
        secondaryParams: Array<out Any?>,
    ): CacheEntryName
}

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

fun combineCacheKey(name: String, params: Array<out Any?>): String =
    combineCacheKey(name, params, nullKeyPart = "<null>")

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

fun combineSecondaryEntryParts(params: Array<out Any?>): String =
    combineSecondaryEntryParts(params, nullKeyPart = "<null>")

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
