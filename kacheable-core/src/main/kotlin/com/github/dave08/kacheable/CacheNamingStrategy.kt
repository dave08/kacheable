package com.github.dave08.kacheable

sealed interface CacheEntryName {
    data class Combined(val key: String) : CacheEntryName

    data class Split(val key: String, val entry: String) : CacheEntryName
}

val CacheEntryName.baseKey: String
    get() =
        when (this) {
            is CacheEntryName.Combined -> key
            is CacheEntryName.Split -> key
        }

val CacheEntryName.entryOrNull: String?
    get() =
        when (this) {
            is CacheEntryName.Combined -> null
            is CacheEntryName.Split -> entry
        }

fun CacheEntryName.requireEntry(): String =
    requireNotNull(entryOrNull) { "Split cache entries require a secondary entry name." }

fun CacheEntryName.withInternalSuffix(suffix: String): String = "$baseKey:$suffix"

fun CacheEntryName.asCombined(): CacheEntryName.Combined = CacheEntryName.Combined(baseKey)

fun interface CacheNamingStrategy {
    fun getEntryName(
        cacheName: String,
        storage: CacheStorage,
        mainParams: Array<out Any>,
        secondaryParams: Array<out Any>,
    ): CacheEntryName
}

fun defaultCacheNamingStrategy(
    combinedKeyCombiner: (cacheName: String, params: Array<out Any>) -> String = ::combineCacheKey,
    mainKeyCombiner: (cacheName: String, params: Array<out Any>) -> String = ::combineCacheKey,
    entryCombiner: (params: Array<out Any>) -> String = ::combineCacheEntryParts,
): CacheNamingStrategy = CacheNamingStrategy { cacheName, storage, mainParams, secondaryParams ->
    when (storage) {
        CacheStorage.String, CacheStorage.List, CacheStorage.Int -> combinedEntry(
            cacheName = cacheName,
            mainParams = mainParams,
            secondaryParams = secondaryParams,
            keyCombiner = combinedKeyCombiner,
        )
        CacheStorage.HashMap, CacheStorage.Set -> splitEntry(
            cacheName = cacheName,
            mainParams = mainParams,
            secondaryParams = secondaryParams,
            mainKeyCombiner = mainKeyCombiner,
            entryCombiner = entryCombiner,
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
    CacheNamingStrategy { cacheName, storage, mainParams, secondaryParams ->
        when (storage) {
            CacheStorage.String, CacheStorage.List, CacheStorage.Int ->
                CacheEntryName.Combined(getName(cacheName, combineParams(mainParams, secondaryParams)))

            CacheStorage.HashMap, CacheStorage.Set ->
                if (secondaryParams.isEmpty()) {
                    CacheEntryName.Combined(getName(cacheName, mainParams))
                } else {
                    CacheEntryName.Split(
                        key = getName(cacheName, mainParams),
                        entry = combineCacheEntryParts(secondaryParams),
                    )
                }
        }
    }

private fun combinedEntry(
    cacheName: String,
    mainParams: Array<out Any>,
    secondaryParams: Array<out Any>,
    keyCombiner: (cacheName: String, params: Array<out Any>) -> String,
): CacheEntryName.Combined = CacheEntryName.Combined(keyCombiner(cacheName, combineParams(mainParams, secondaryParams)))

private fun splitEntry(
    cacheName: String,
    mainParams: Array<out Any>,
    secondaryParams: Array<out Any>,
    mainKeyCombiner: (cacheName: String, params: Array<out Any>) -> String,
    entryCombiner: (params: Array<out Any>) -> String,
): CacheEntryName =
    if (secondaryParams.isEmpty()) {
        CacheEntryName.Combined(mainKeyCombiner(cacheName, mainParams))
    } else {
        CacheEntryName.Split(
            key = mainKeyCombiner(cacheName, mainParams),
            entry = entryCombiner(secondaryParams),
        )
    }

fun combineCacheKey(name: String, params: Array<out Any>): String =
    if (params.isEmpty()) {
        name
    } else {
        "$name:${params.joinToString(",")}"
    }

fun combineCacheEntryParts(params: Array<out Any>): String = params.joinToString(",")

private fun combineParams(
    mainParams: Array<out Any>,
    secondaryParams: Array<out Any>,
): Array<out Any> = buildList {
    addAll(mainParams.asList())
    addAll(secondaryParams.asList())
}.toTypedArray()
