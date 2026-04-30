package com.github.dave08.kacheable

sealed interface CacheEntryName {
    data class Flat(val key: String) : CacheEntryName

    data class Layered(val key: String, val entry: String) : CacheEntryName
}

val CacheEntryName.cacheKey: String
    get() =
        when (this) {
            is CacheEntryName.Flat -> key
            is CacheEntryName.Layered -> key
        }

val CacheEntryName.secondaryEntryOrNull: String?
    get() =
        when (this) {
            is CacheEntryName.Flat -> null
            is CacheEntryName.Layered -> entry
        }

fun CacheEntryName.requireSecondaryEntry(): String =
    requireNotNull(secondaryEntryOrNull) { "Layered cache entries require a secondary entry name." }

fun CacheEntryName.withInternalSuffix(suffix: String): String = "$cacheKey:$suffix"

fun CacheEntryName.asFlat(): CacheEntryName.Flat = CacheEntryName.Flat(cacheKey)

fun interface CacheNamingStrategy {
    fun getEntryName(
        cacheName: String,
        storage: CacheStorage,
        primaryParams: Array<out Any>,
        secondaryParams: Array<out Any>,
    ): CacheEntryName
}

fun defaultCacheNamingStrategy(
    flatKeyCombiner: (cacheName: String, params: Array<out Any>) -> String = ::combineCacheKey,
    primaryKeyCombiner: (cacheName: String, params: Array<out Any>) -> String = ::combineCacheKey,
    secondaryEntryCombiner: (params: Array<out Any>) -> String = ::combineSecondaryEntryParts,
): CacheNamingStrategy = CacheNamingStrategy { cacheName, storage, primaryParams, secondaryParams ->
    when (storage) {
        CacheStorage.String -> flatEntry(
            cacheName = cacheName,
            primaryParams = primaryParams,
            secondaryParams = secondaryParams,
            keyCombiner = flatKeyCombiner,
        )
        CacheStorage.HashMap, CacheStorage.Set -> layeredEntry(
            cacheName = cacheName,
            primaryParams = primaryParams,
            secondaryParams = secondaryParams,
            primaryKeyCombiner = primaryKeyCombiner,
            secondaryEntryCombiner = secondaryEntryCombiner,
        )
    }
}

@Deprecated(
    message = "Use CacheNamingStrategy instead.",
    replaceWith = ReplaceWith("CacheNamingStrategy"),
)
fun interface GetNameStrategy {
    fun getName(name: String, params: Array<out Any>): String
}

@Deprecated(
    message = "Use defaultCacheNamingStrategy() instead.",
    replaceWith = ReplaceWith("defaultCacheNamingStrategy()", imports = ["com.github.dave08.kacheable.defaultCacheNamingStrategy"]),
)
val DefaultGetNameStrategy: GetNameStrategy = GetNameStrategy { name, params ->
    combineCacheKey(name, params)
}

fun GetNameStrategy.asCacheNamingStrategy(): CacheNamingStrategy =
    CacheNamingStrategy { cacheName, storage, primaryParams, secondaryParams ->
        when (storage) {
            CacheStorage.String ->
                CacheEntryName.Flat(getName(cacheName, combineParams(primaryParams, secondaryParams)))

            CacheStorage.HashMap, CacheStorage.Set ->
                if (secondaryParams.isEmpty()) {
                    CacheEntryName.Flat(getName(cacheName, primaryParams))
                } else {
                    CacheEntryName.Layered(
                        key = getName(cacheName, primaryParams),
                        entry = combineSecondaryEntryParts(secondaryParams),
                    )
                }
        }
    }

private fun flatEntry(
    cacheName: String,
    primaryParams: Array<out Any>,
    secondaryParams: Array<out Any>,
    keyCombiner: (cacheName: String, params: Array<out Any>) -> String,
): CacheEntryName.Flat = CacheEntryName.Flat(keyCombiner(cacheName, combineParams(primaryParams, secondaryParams)))

private fun layeredEntry(
    cacheName: String,
    primaryParams: Array<out Any>,
    secondaryParams: Array<out Any>,
    primaryKeyCombiner: (cacheName: String, params: Array<out Any>) -> String,
    secondaryEntryCombiner: (params: Array<out Any>) -> String,
): CacheEntryName =
    if (secondaryParams.isEmpty()) {
        CacheEntryName.Flat(primaryKeyCombiner(cacheName, primaryParams))
    } else {
        CacheEntryName.Layered(
            key = primaryKeyCombiner(cacheName, primaryParams),
            entry = secondaryEntryCombiner(secondaryParams),
        )
    }

fun combineCacheKey(name: String, params: Array<out Any>): String =
    if (params.isEmpty()) {
        name
    } else {
        "$name:${params.joinToString(",")}"
    }

fun combineSecondaryEntryParts(params: Array<out Any>): String = params.joinToString(",")

private fun combineParams(
    primaryParams: Array<out Any>,
    secondaryParams: Array<out Any>,
): Array<out Any> = buildList {
    addAll(primaryParams.asList())
    addAll(secondaryParams.asList())
}.toTypedArray()
