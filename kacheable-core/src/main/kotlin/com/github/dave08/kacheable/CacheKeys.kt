package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.keys.cacheArgs
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

/**
 * Storage override that is valid for exact cache keys.
 */
sealed interface ExactStoragePlan<out R>

/**
 * Storage override that is valid for partitioned cache keys.
 */
sealed interface IndexedStoragePlan<out R>

data object AutoStoragePlan : ExactStoragePlan<Nothing>, IndexedStoragePlan<Nothing>

class ExactValueStoragePlan<R> internal constructor() : ExactStoragePlan<R>

class IndexedValueStoragePlan<R> internal constructor() : IndexedStoragePlan<R>

class MembershipStoragePlan internal constructor(
    val cacheFalse: Boolean,
) : IndexedStoragePlan<Boolean>

class EnumMembershipStoragePlan<E : Enum<E>> @PublishedApi internal constructor(
    val returnView: EnumMemberCacheReturn<E>,
) : IndexedStoragePlan<E>

/**
 * Lets Kacheable choose the storage plan from the key shape and result type.
 *
 * With `auto()`, exact keys use value storage. Partitioned Boolean results use membership storage,
 * partitioned enum results use enum membership storage, and other partitioned results use indexed
 * value storage.
 */
fun auto(): AutoStoragePlan = AutoStoragePlan

/**
 * Stores each exact cache result as one serialized value.
 *
 * Example:
 *
 * ```kotlin
 * cacheKey("artist-songs", returns<List<Song>>(), key = exact(artistId))
 * ```
 *
 * The `List<Song>` is one cached value, not many indexed songs.
 */
fun <R> exactValueStorage(): ExactValueStoragePlan<R> = ExactValueStoragePlan()

/**
 * Stores each partitioned entry as a serialized value inside its partition.
 *
 * Example:
 *
 * ```kotlin
 * cacheKey("artist-page", returns<List<Song>>(), key = partitioned(artistId, page))
 * ```
 *
 * Each page is one serialized value inside the artist partition.
 */
fun <R> indexedValueStorage(): IndexedValueStoragePlan<R> = IndexedValueStoragePlan()

/**
 * Stores Boolean partitioned results as membership state.
 *
 * When [cacheFalse] is `false`, false results are returned to the caller but not written to the
 * cache.
 *
 * Example:
 *
 * ```kotlin
 * cacheKey(
 *     "artist-follow",
 *     returns<Boolean>(),
 *     key = partitioned(partition = artistId, key = accountId),
 *     storage = membershipStorage(cacheFalse = false),
 * )
 * ```
 */
fun membershipStorage(cacheFalse: Boolean = true): MembershipStoragePlan = MembershipStoragePlan(cacheFalse)

/**
 * Stores enum partitioned results as classification sets, one set per enum value.
 *
 * The public cache result is still the enum value. The set layout is only the storage plan.
 *
 * ```kotlin
 * cacheKey(
 *     "song-reaction",
 *     returns<Reaction>(),
 *     key = partitioned(partition = songId, key = accountId),
 *     storage = enumMembershipStorage<Reaction>(),
 * )
 * ```
 */
inline fun <reified E : Enum<E>> enumMembershipStorage(
    values: List<E> = enumValues<E>().toList(),
    noinline valueName: (E) -> String = { it.name },
): EnumMembershipStoragePlan<E> = EnumMembershipStoragePlan(EnumMemberCacheReturn(values, valueName, serializer<E>()))

/**
 * Common type for refs that can be passed to typed invalidation calls.
 */
sealed interface CacheInvalidationRef

/**
 * Exact raw string-cache entry ref, intended as a migration escape hatch.
 */
class RawCacheEntryRef internal constructor(
    internal val entryRef: StoredCacheEntryRef<CacheStorage.String>,
) : CacheInvalidationRef {
    override fun toString(): String = entryRef.toDebugString()
}

/**
 * Whole raw string-cache ref, intended as a migration escape hatch.
 */
class RawCacheRef internal constructor(
    internal val allRef: StoredCacheAllRef<CacheStorage.String>,
) : CacheInvalidationRef {
    override fun toString(): String = allRef.toDebugString()
}

/**
 * Ref to one logical cached result.
 */
class CacheEntryRef<R> internal constructor(
    internal val entryRef: StoredCacheEntryRef<CacheStorage>,
    internal val returnView: CacheReturn<R, *>,
) : CacheInvalidationRef {
    override fun toString(): String = entryRef.toDebugString()
}

/**
 * Ref to a partition or matching subset of a partitioned cache key.
 */
class CachePartRef<R> internal constructor(
    internal val partRef: StoredCachePartRef<CacheStorage>,
    internal val returnView: CacheReturn<R, *>,
) : CacheInvalidationRef {
    override fun toString(): String = partRef.toDebugString()
}

/**
 * Ref to all entries belonging to one typed cache key.
 */
class CacheAllRef<R> internal constructor(
    internal val allRef: StoredCacheAllRef<CacheStorage>,
) : CacheInvalidationRef {
    override fun toString(): String = allRef.toDebugString()
}

private fun StoredCacheEntryRef<*>.toDebugString(): String =
    "${name}${cacheArgs.debugParams()}"

private fun StoredCachePartRef<*>.toDebugString(): String =
    if (secondaryPatternPartArgs == null) {
        "${name}.partition${cacheArgs.primaryPartArgs.debugParams()}"
    } else {
        "${name}.matching${(cacheArgs.primaryPartArgs + secondaryPatternPartArgs.orEmpty()).debugParams()}"
    }

private fun StoredCacheAllRef<*>.toDebugString(): String = "${name}.all()"

private fun PrimarySecondaryCacheArgs.debugParams(): String =
    (primaryPartArgs + secondaryPartArgs).debugParams()

private fun List<CacheArgs>.debugParams(): String =
    flatMap { it.toParamsArray().asList() }
        .joinToString(prefix = "(", postfix = ")") { it?.toString() ?: "<null>" }

/**
 * Creates a raw string-cache entry ref for migration code that has not moved to typed cache keys.
 */
fun rawCacheEntry(
    cacheName: String,
    vararg params: Any?,
): RawCacheEntryRef = RawCacheEntryRef(
    StoredEntryRef(
        name = cacheName,
        cacheArgs = cacheArgs(
            primaryPartArgs = params.map { argsOf(it) },
            primaryPartNames = List(params.size) { null },
        ),
        storage = CacheStorage.String,
    ),
)

/**
 * Creates a raw string-cache ref for invalidating a whole legacy cache namespace.
 */
fun rawCache(
    cacheName: String,
): RawCacheRef = RawCacheRef(StoredAllRef(cacheName, CacheStorage.String))

/**
 * Result descriptor used by [cacheKey] to choose serialization and automatic storage.
 *
 * Users normally create this with [returns] or [returnsEnum].
 */
class CacheResult<R> @PublishedApi internal constructor(
    @PublishedApi internal val resultClass: KClass<*>,
    @PublishedApi internal val isNullable: Boolean,
    @PublishedApi internal val valueReturn: ValueCacheReturn<R>,
    @PublishedApi internal val enumReturn: EnumMemberCacheReturn<*>?,
)

@PublishedApi
internal data class PlannedStorage<R>(
    val storage: CacheStorage,
    val returnView: CacheReturn<R, *>,
)

@PublishedApi
internal inline fun <reified R> cacheResult(): CacheResult<R> =
    CacheResult(
        resultClass = typeOf<R>().classifier as KClass<*>,
        isNullable = typeOf<R>().isMarkedNullable,
        valueReturn = lazyValue<R>(),
        enumReturn = enumMemberReturnOrNull<R>(),
    )

/**
 * Describes the result type returned from a typed cache key.
 *
 * Example:
 *
 * ```kotlin
 * val songCache = cacheKey("song", returns<Song>(), key = exact(songId))
 * val songsCache = cacheKey("artist-songs", returns<List<Song>>(), key = exact(artistId))
 * ```
 *
 * Collections are ordinary results. `returns<List<Song>>()` means one cached list for each key.
 */
inline fun <reified R> returns(): CacheResult<R> = cacheResult()

/**
 * Describes an enum result and optionally customizes the enum values/names used by membership
 * storage.
 *
 * `returns<EnumType>()` is enough for automatic enum membership. Use `returnsEnum(...)` when you
 * want to make the enum universe or stored enum names explicit.
 */
inline fun <reified E : Enum<E>> returnsEnum(
    values: List<E> = enumValues<E>().toList(),
    noinline valueName: (E) -> String = { it.name },
): CacheResult<E> =
    CacheResult(
        resultClass = E::class,
        isNullable = false,
        valueReturn = lazyValue<E>(),
        enumReturn = EnumMemberCacheReturn(values, valueName, { serializer<E>() }),
    )

/**
 * Key part that can be used for matching invalidations inside a partition.
 *
 * A matchable key part is still an ordinary key part for reads. The extra marker only allows calls
 * like `cache.invalidate(pageCache.matching(artistId, locale("he")))`.
 */
interface MatchableKeyPart<P> : KeyPart<P> {
    override fun invoke(value: P): MatchableKeyPartValue = MatchableKeyPartValue(this, encode(value))
}

class MatchableKeyPartValue(
    override val keyPart: MatchableKeyPart<*>,
    override val args: CacheArgs,
) : KeyPartValue(keyPart, args)

@PublishedApi
internal data class SimpleMatchableKeyPart<P>(
    private val delegate: KeyPart<P>,
) : MatchableKeyPart<P> {
    override val name: String? = delegate.name
    override val segmentCount: Int? = delegate.segmentCount
    override fun encode(value: P): CacheArgs = delegate.encode(value)
}

/**
 * Creates a named key part that may be used by `matching(...)` invalidations.
 *
 * Example:
 *
 * ```kotlin
 * val locale = matchableKeyPart<String>("locale")
 * val pages = cacheKey("artist-pages", returns<Page>(), key = partitioned(artistId, page + locale))
 *
 * cache.invalidate(pages.matching(artistIdValue, locale("he")))
 * ```
 *
 * Matching is scoped key matching inside a partition, not value search.
 */
fun <P> matchableKeyPart(name: String): MatchableKeyPart<P> =
    SimpleMatchableKeyPart(keyPart(name))

/**
 * Creates a named multi-segment key part that may be used by `matching(...)` invalidations.
 *
 * Example:
 *
 * ```kotlin
 * val paging = matchableKeyPart<Page>("page", Page::offset, Page::limit)
 * ```
 */
fun <P> matchableKeyPart(
    name: String,
    vararg values: (P) -> Any?,
): MatchableKeyPart<P> = SimpleMatchableKeyPart(keyPart(name, *values))

/**
 * Shape for exact cache keys: one logical result is identified directly by the key values.
 */
sealed interface ExactKeyShape

/**
 * Shape for partitioned cache keys: a partition identifies related entries, and an entry key
 * identifies one logical result inside that partition.
 */
sealed interface PartitionedKeyShape {
    val hasMatchableEntryParts: Boolean
}

data object ExactKeyShape0 : ExactKeyShape

class ExactKeyShape1<P1> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPart<P1>,
) : ExactKeyShape

class ExactKeyShape2<P1, P2> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition2<P1, P2>,
) : ExactKeyShape

class ExactKeyShape3<P1, P2, P3> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition3<P1, P2, P3>,
) : ExactKeyShape

class ExactKeyShape4<P1, P2, P3, P4> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition4<P1, P2, P3, P4>,
) : ExactKeyShape

class ExactKeyShape5<P1, P2, P3, P4, P5> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
) : ExactKeyShape

class ExactKeyShape6<P1, P2, P3, P4, P5, P6> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
) : ExactKeyShape

class SinglePartitionKeyShape1<K1> @PublishedApi internal constructor(
    @PublishedApi internal val itemKey: KeyPart<K1>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.isMatchable()
}

class SinglePartitionKeyShape2<K1, K2> @PublishedApi internal constructor(
    @PublishedApi internal val itemKey: KeyPartComposition2<K1, K2>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class SinglePartitionKeyShape3<K1, K2, K3> @PublishedApi internal constructor(
    @PublishedApi internal val itemKey: KeyPartComposition3<K1, K2, K3>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class SinglePartitionKeyShape4<K1, K2, K3, K4> @PublishedApi internal constructor(
    @PublishedApi internal val itemKey: KeyPartComposition4<K1, K2, K3, K4>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class SinglePartitionKeyShape5<K1, K2, K3, K4, K5> @PublishedApi internal constructor(
    @PublishedApi internal val itemKey: KeyPartComposition5<K1, K2, K3, K4, K5>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class SinglePartitionKeyShape6<K1, K2, K3, K4, K5, K6> @PublishedApi internal constructor(
    @PublishedApi internal val itemKey: KeyPartComposition6<K1, K2, K3, K4, K5, K6>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape1x1<I1, K1> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPart<I1>,
    @PublishedApi internal val itemKey: KeyPart<K1>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.isMatchable()
}

class PartitionedKeyShape1x2<I1, K1, K2> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPart<I1>,
    @PublishedApi internal val itemKey: KeyPartComposition2<K1, K2>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape1x3<I1, K1, K2, K3> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPart<I1>,
    @PublishedApi internal val itemKey: KeyPartComposition3<K1, K2, K3>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape1x4<I1, K1, K2, K3, K4> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPart<I1>,
    @PublishedApi internal val itemKey: KeyPartComposition4<K1, K2, K3, K4>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape1x5<I1, K1, K2, K3, K4, K5> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPart<I1>,
    @PublishedApi internal val itemKey: KeyPartComposition5<K1, K2, K3, K4, K5>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape2x1<I1, I2, K1> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition2<I1, I2>,
    @PublishedApi internal val itemKey: KeyPart<K1>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.isMatchable()
}

class PartitionedKeyShape2x2<I1, I2, K1, K2> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition2<I1, I2>,
    @PublishedApi internal val itemKey: KeyPartComposition2<K1, K2>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape2x3<I1, I2, K1, K2, K3> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition2<I1, I2>,
    @PublishedApi internal val itemKey: KeyPartComposition3<K1, K2, K3>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape2x4<I1, I2, K1, K2, K3, K4> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition2<I1, I2>,
    @PublishedApi internal val itemKey: KeyPartComposition4<K1, K2, K3, K4>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape3x1<I1, I2, I3, K1> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition3<I1, I2, I3>,
    @PublishedApi internal val itemKey: KeyPart<K1>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.isMatchable()
}

class PartitionedKeyShape3x2<I1, I2, I3, K1, K2> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition3<I1, I2, I3>,
    @PublishedApi internal val itemKey: KeyPartComposition2<K1, K2>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape3x3<I1, I2, I3, K1, K2, K3> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition3<I1, I2, I3>,
    @PublishedApi internal val itemKey: KeyPartComposition3<K1, K2, K3>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape4x1<I1, I2, I3, I4, K1> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition4<I1, I2, I3, I4>,
    @PublishedApi internal val itemKey: KeyPart<K1>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.isMatchable()
}

class PartitionedKeyShape4x2<I1, I2, I3, I4, K1, K2> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition4<I1, I2, I3, I4>,
    @PublishedApi internal val itemKey: KeyPartComposition2<K1, K2>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

class PartitionedKeyShape5x1<I1, I2, I3, I4, I5, K1> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition5<I1, I2, I3, I4, I5>,
    @PublishedApi internal val itemKey: KeyPart<K1>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.isMatchable()
}

/**
 * Defines a no-argument exact cache key shape.
 *
 * Example:
 *
 * ```kotlin
 * val settings = cacheKey("settings", returns<AppSettings>(), key = exact())
 * cache(settings()) { loadSettings() }
 * ```
 */
fun exact(): ExactKeyShape0 = ExactKeyShape0

/**
 * Defines an exact cache key shape from one key part.
 *
 * Use exact keys when the supplied values directly identify one cached result.
 *
 * ```kotlin
 * val songCache = cacheKey("song", returns<Song>(), key = exact(songId))
 * ```
 */
fun <P1> exact(
    key: KeyPart<P1>,
): ExactKeyShape1<P1> = ExactKeyShape1(key)

fun <P1, P2> exact(
    key: KeyPartComposition2<P1, P2>,
): ExactKeyShape2<P1, P2> = ExactKeyShape2(key)

fun <P1, P2, P3> exact(
    key: KeyPartComposition3<P1, P2, P3>,
): ExactKeyShape3<P1, P2, P3> = ExactKeyShape3(key)

fun <P1, P2, P3, P4> exact(
    key: KeyPartComposition4<P1, P2, P3, P4>,
): ExactKeyShape4<P1, P2, P3, P4> = ExactKeyShape4(key)

fun <P1, P2, P3, P4, P5> exact(
    key: KeyPartComposition5<P1, P2, P3, P4, P5>,
): ExactKeyShape5<P1, P2, P3, P4, P5> = ExactKeyShape5(key)

fun <P1, P2, P3, P4, P5, P6> exact(
    key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
): ExactKeyShape6<P1, P2, P3, P4, P5, P6> = ExactKeyShape6(key)

/**
 * Defines a partitioned cache key shape from a partition part and an entry key part.
 *
 * Read `partitioned(partition = artistId, key = page)` as: one cached result per `page` inside
 * one `artistId` partition.
 *
 * ```kotlin
 * val pages = cacheKey("artist-pages", returns<List<Song>>(), key = partitioned(artistId, page))
 *
 * cache.invalidate(pages(artistIdValue, pageValue))      // one page
 * cache.invalidate(pages.partition(artistIdValue))       // all pages for the artist
 * ```
 */
fun <I1, K1> partitioned(
    partition: KeyPart<I1>,
    key: KeyPart<K1>,
): PartitionedKeyShape1x1<I1, K1> = PartitionedKeyShape1x1(partition, key)

/**
 * Defines a partitioned cache key with no explicit partition; useful when storage should still
 * support all-entry invalidation or membership/indexed storage for the whole cache.
 *
 * Example:
 *
 * ```kotlin
 * val newest = cacheKey("newest-videos", returns<List<Video>>(), key = partitioned(key = page))
 * cache.invalidate(newest.partition()) // all pages in the cache family
 * ```
 */
fun <K1> partitioned(
    key: KeyPart<K1>,
): SinglePartitionKeyShape1<K1> = SinglePartitionKeyShape1(key)

fun <K1, K2> partitioned(
    key: KeyPartComposition2<K1, K2>,
): SinglePartitionKeyShape2<K1, K2> = SinglePartitionKeyShape2(key)

fun <K1, K2, K3> partitioned(
    key: KeyPartComposition3<K1, K2, K3>,
): SinglePartitionKeyShape3<K1, K2, K3> = SinglePartitionKeyShape3(key)

fun <K1, K2, K3, K4> partitioned(
    key: KeyPartComposition4<K1, K2, K3, K4>,
): SinglePartitionKeyShape4<K1, K2, K3, K4> = SinglePartitionKeyShape4(key)

fun <K1, K2, K3, K4, K5> partitioned(
    key: KeyPartComposition5<K1, K2, K3, K4, K5>,
): SinglePartitionKeyShape5<K1, K2, K3, K4, K5> = SinglePartitionKeyShape5(key)

fun <K1, K2, K3, K4, K5, K6> partitioned(
    key: KeyPartComposition6<K1, K2, K3, K4, K5, K6>,
): SinglePartitionKeyShape6<K1, K2, K3, K4, K5, K6> = SinglePartitionKeyShape6(key)

fun <I1, K1, K2> partitioned(
    partition: KeyPart<I1>,
    key: KeyPartComposition2<K1, K2>,
): PartitionedKeyShape1x2<I1, K1, K2> = PartitionedKeyShape1x2(partition, key)

fun <I1, K1, K2, K3> partitioned(
    partition: KeyPart<I1>,
    key: KeyPartComposition3<K1, K2, K3>,
): PartitionedKeyShape1x3<I1, K1, K2, K3> = PartitionedKeyShape1x3(partition, key)

fun <I1, K1, K2, K3, K4> partitioned(
    partition: KeyPart<I1>,
    key: KeyPartComposition4<K1, K2, K3, K4>,
): PartitionedKeyShape1x4<I1, K1, K2, K3, K4> = PartitionedKeyShape1x4(partition, key)

fun <I1, K1, K2, K3, K4, K5> partitioned(
    partition: KeyPart<I1>,
    key: KeyPartComposition5<K1, K2, K3, K4, K5>,
): PartitionedKeyShape1x5<I1, K1, K2, K3, K4, K5> = PartitionedKeyShape1x5(partition, key)

fun <I1, I2, K1> partitioned(
    partition: KeyPartComposition2<I1, I2>,
    key: KeyPart<K1>,
): PartitionedKeyShape2x1<I1, I2, K1> = PartitionedKeyShape2x1(partition, key)

fun <I1, I2, K1, K2> partitioned(
    partition: KeyPartComposition2<I1, I2>,
    key: KeyPartComposition2<K1, K2>,
): PartitionedKeyShape2x2<I1, I2, K1, K2> = PartitionedKeyShape2x2(partition, key)

fun <I1, I2, K1, K2, K3> partitioned(
    partition: KeyPartComposition2<I1, I2>,
    key: KeyPartComposition3<K1, K2, K3>,
): PartitionedKeyShape2x3<I1, I2, K1, K2, K3> = PartitionedKeyShape2x3(partition, key)

fun <I1, I2, K1, K2, K3, K4> partitioned(
    partition: KeyPartComposition2<I1, I2>,
    key: KeyPartComposition4<K1, K2, K3, K4>,
): PartitionedKeyShape2x4<I1, I2, K1, K2, K3, K4> = PartitionedKeyShape2x4(partition, key)

fun <I1, I2, I3, K1> partitioned(
    partition: KeyPartComposition3<I1, I2, I3>,
    key: KeyPart<K1>,
): PartitionedKeyShape3x1<I1, I2, I3, K1> = PartitionedKeyShape3x1(partition, key)

fun <I1, I2, I3, K1, K2> partitioned(
    partition: KeyPartComposition3<I1, I2, I3>,
    key: KeyPartComposition2<K1, K2>,
): PartitionedKeyShape3x2<I1, I2, I3, K1, K2> = PartitionedKeyShape3x2(partition, key)

fun <I1, I2, I3, K1, K2, K3> partitioned(
    partition: KeyPartComposition3<I1, I2, I3>,
    key: KeyPartComposition3<K1, K2, K3>,
): PartitionedKeyShape3x3<I1, I2, I3, K1, K2, K3> = PartitionedKeyShape3x3(partition, key)

fun <I1, I2, I3, I4, K1> partitioned(
    partition: KeyPartComposition4<I1, I2, I3, I4>,
    key: KeyPart<K1>,
): PartitionedKeyShape4x1<I1, I2, I3, I4, K1> = PartitionedKeyShape4x1(partition, key)

fun <I1, I2, I3, I4, K1, K2> partitioned(
    partition: KeyPartComposition4<I1, I2, I3, I4>,
    key: KeyPartComposition2<K1, K2>,
): PartitionedKeyShape4x2<I1, I2, I3, I4, K1, K2> = PartitionedKeyShape4x2(partition, key)

fun <I1, I2, I3, I4, I5, K1> partitioned(
    partition: KeyPartComposition5<I1, I2, I3, I4, I5>,
    key: KeyPart<K1>,
): PartitionedKeyShape5x1<I1, I2, I3, I4, I5, K1> = PartitionedKeyShape5x1(partition, key)

@PublishedApi
internal fun <R> planExact(
    result: CacheResult<R>,
    storage: ExactStoragePlan<R>,
): PlannedStorage<R> =
    when (storage) {
        is AutoStoragePlan,
        is ExactValueStoragePlan<R>,
        -> PlannedStorage(CacheStorage.String, result.valueReturn)
    }

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <R> planIndexed(
    result: CacheResult<R>,
    storage: IndexedStoragePlan<R>,
    hasMatchableEntryParts: Boolean,
): PlannedStorage<R> =
    when (storage) {
        is AutoStoragePlan -> when {
            hasMatchableEntryParts -> PlannedStorage(CacheStorage.HashMap, result.valueReturn)
            !result.isNullable && result.resultClass == Boolean::class -> PlannedStorage(CacheStorage.Set, IsMemberCacheReturn() as CacheReturn<R, *>)
            !result.isNullable && result.enumReturn != null -> PlannedStorage(CacheStorage.Set, result.enumReturn as CacheReturn<R, *>)
            else -> PlannedStorage(CacheStorage.HashMap, result.valueReturn)
        }
        is IndexedValueStoragePlan<R> -> PlannedStorage(CacheStorage.HashMap, result.valueReturn)
        is MembershipStoragePlan -> PlannedStorage(CacheStorage.Set, IsMemberCacheReturn(storage.cacheFalse) as CacheReturn<R, *>)
        is EnumMembershipStoragePlan<*> -> PlannedStorage(CacheStorage.Set, storage.returnView as CacheReturn<R, *>)
    }

@PublishedApi
internal inline fun <reified R : Any> enumMemberReturn(): EnumMemberCacheReturn<R> {
    val values = requireNotNull(R::class.java.enumConstants?.toList()) {
        "Enum membership storage requires an enum result type."
    }
    return EnumMemberCacheReturn(
        values = values,
        valueName = { value -> (value as Enum<*>).name },
        serializerProvider = { serializer<R>() },
    )
}

@PublishedApi
@Suppress("UNCHECKED_CAST")
internal inline fun <reified R> enumMemberReturnOrNull(): EnumMemberCacheReturn<*>? {
    val resultType = typeOf<R>()
    if (resultType.isMarkedNullable) return null
    val resultClass = resultType.classifier as? KClass<*> ?: return null
    val values = resultClass.java.enumConstants?.toList() ?: return null
    return EnumMemberCacheReturn(
        values = values,
        valueName = { value -> (value as Enum<*>).name },
        serializerProvider = { serializer<R>() as kotlinx.serialization.KSerializer<Any> },
    )
}

private fun entryRef(
    name: String,
    storage: CacheStorage,
    partitionPartArgs: List<CacheArgs>,
    partitionPartNames: List<String?>,
    itemKeyPartArgs: List<CacheArgs> = emptyList(),
    itemKeyPartNames: List<String?> = emptyList(),
): StoredCacheEntryRef<CacheStorage> = StoredEntryRef(
    name = name,
    cacheArgs = cacheArgs(
        primaryPartArgs = partitionPartArgs,
        primaryPartNames = partitionPartNames,
        secondaryPartArgs = itemKeyPartArgs,
        secondaryPartNames = itemKeyPartNames,
    ),
    storage = storage,
)

private fun partRef(
    name: String,
    storage: CacheStorage,
    partitionPartArgs: List<CacheArgs>,
    partitionPartNames: List<String?>,
    itemKeyPatternPartArgs: List<CacheArgs>? = null,
): StoredCachePartRef<CacheStorage> = StoredPartRef(
    name = name,
    args = joinArgs(*partitionPartArgs.toTypedArray()),
    cacheArgs = cacheArgs(
        primaryPartArgs = partitionPartArgs,
        primaryPartNames = partitionPartNames,
    ),
    storage = storage,
    secondaryPatternPartArgs = itemKeyPatternPartArgs,
)

private fun <R> cacheEntryRef(
    name: String,
    plannedStorage: PlannedStorage<R>,
    partitionPartArgs: List<CacheArgs>,
    partitionPartNames: List<String?>,
    itemKeyPartArgs: List<CacheArgs> = emptyList(),
    itemKeyPartNames: List<String?> = emptyList(),
): CacheEntryRef<R> = CacheEntryRef(
    entryRef = entryRef(
        name = name,
        storage = plannedStorage.storage,
        partitionPartArgs = partitionPartArgs,
        partitionPartNames = partitionPartNames,
        itemKeyPartArgs = itemKeyPartArgs,
        itemKeyPartNames = itemKeyPartNames,
    ),
    returnView = plannedStorage.returnView,
)

private fun <R> cachePartRef(
    name: String,
    plannedStorage: PlannedStorage<R>,
    partitionPartArgs: List<CacheArgs>,
    partitionPartNames: List<String?>,
    itemKeyPatternPartArgs: List<CacheArgs>? = null,
): CachePartRef<R> = CachePartRef(
    partRef = partRef(
        name = name,
        storage = plannedStorage.storage,
        partitionPartArgs = partitionPartArgs,
        partitionPartNames = partitionPartNames,
        itemKeyPatternPartArgs = itemKeyPatternPartArgs,
    ),
    returnView = plannedStorage.returnView,
)

private fun <R> cacheAllRef(
    name: String,
    plannedStorage: PlannedStorage<R>,
): CacheAllRef<R> = CacheAllRef(
    allRef = StoredAllRef(name, plannedStorage.storage),
)

/**
 * Creates a typed cache key. Exact shapes store one complete logical result per key.
 */
fun <R> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape0,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey0<R> = ExactCacheKey0(name, planExact(returns, storage))

fun <R, P1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape1<P1>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey1<P1, R> = ExactCacheKey1(name, key.key, planExact(returns, storage))

fun <R, P1, P2> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape2<P1, P2>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey2<P1, P2, R> = ExactCacheKey2(name, key.key, planExact(returns, storage))

fun <R, P1, P2, P3> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape3<P1, P2, P3>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey3<P1, P2, P3, R> = ExactCacheKey3(name, key.key, planExact(returns, storage))

fun <R, P1, P2, P3, P4> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape4<P1, P2, P3, P4>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey4<P1, P2, P3, P4, R> = ExactCacheKey4(name, key.key, planExact(returns, storage))

fun <R, P1, P2, P3, P4, P5> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape5<P1, P2, P3, P4, P5>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey5<P1, P2, P3, P4, P5, R> = ExactCacheKey5(name, key.key, planExact(returns, storage))

fun <R, P1, P2, P3, P4, P5, P6> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape6<P1, P2, P3, P4, P5, P6>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey6<P1, P2, P3, P4, P5, P6, R> = ExactCacheKey6(name, key.key, planExact(returns, storage))

/**
 * Creates a typed cache key. Partitioned shapes allow invalidating a partition or matching entry
 * parts inside a partition.
 */
fun <R, I1, K1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape1x1<I1, K1>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey1x1<I1, K1, R> =
    PartitionedCacheKey1x1(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

/**
 * Creates a typed single-partition cache key. Entries share one implicit cache-wide partition.
 */
fun <R, K1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: SinglePartitionKeyShape1<K1>,
    storage: IndexedStoragePlan<R> = auto(),
): SinglePartitionCacheKey1<K1, R> =
    SinglePartitionCacheKey1(name, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, K1, K2> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: SinglePartitionKeyShape2<K1, K2>,
    storage: IndexedStoragePlan<R> = auto(),
): SinglePartitionCacheKey2<K1, K2, R> =
    SinglePartitionCacheKey2(name, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, K1, K2, K3> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: SinglePartitionKeyShape3<K1, K2, K3>,
    storage: IndexedStoragePlan<R> = auto(),
): SinglePartitionCacheKey3<K1, K2, K3, R> =
    SinglePartitionCacheKey3(name, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, K1, K2, K3, K4> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: SinglePartitionKeyShape4<K1, K2, K3, K4>,
    storage: IndexedStoragePlan<R> = auto(),
): SinglePartitionCacheKey4<K1, K2, K3, K4, R> =
    SinglePartitionCacheKey4(name, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, K1, K2, K3, K4, K5> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: SinglePartitionKeyShape5<K1, K2, K3, K4, K5>,
    storage: IndexedStoragePlan<R> = auto(),
): SinglePartitionCacheKey5<K1, K2, K3, K4, K5, R> =
    SinglePartitionCacheKey5(name, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, K1, K2, K3, K4, K5, K6> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: SinglePartitionKeyShape6<K1, K2, K3, K4, K5, K6>,
    storage: IndexedStoragePlan<R> = auto(),
): SinglePartitionCacheKey6<K1, K2, K3, K4, K5, K6, R> =
    SinglePartitionCacheKey6(name, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, K1, K2> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape1x2<I1, K1, K2>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey1x2<I1, K1, K2, R> =
    PartitionedCacheKey1x2(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, K1, K2, K3> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape1x3<I1, K1, K2, K3>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey1x3<I1, K1, K2, K3, R> =
    PartitionedCacheKey1x3(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, K1, K2, K3, K4> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape1x4<I1, K1, K2, K3, K4>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey1x4<I1, K1, K2, K3, K4, R> =
    PartitionedCacheKey1x4(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, K1, K2, K3, K4, K5> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape1x5<I1, K1, K2, K3, K4, K5>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey1x5<I1, K1, K2, K3, K4, K5, R> =
    PartitionedCacheKey1x5(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, I2, K1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape2x1<I1, I2, K1>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey2x1<I1, I2, K1, R> =
    PartitionedCacheKey2x1(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, I2, K1, K2> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape2x2<I1, I2, K1, K2>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey2x2<I1, I2, K1, K2, R> =
    PartitionedCacheKey2x2(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, I2, K1, K2, K3> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape2x3<I1, I2, K1, K2, K3>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey2x3<I1, I2, K1, K2, K3, R> =
    PartitionedCacheKey2x3(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, I2, K1, K2, K3, K4> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape2x4<I1, I2, K1, K2, K3, K4>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey2x4<I1, I2, K1, K2, K3, K4, R> =
    PartitionedCacheKey2x4(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, I2, I3, K1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape3x1<I1, I2, I3, K1>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey3x1<I1, I2, I3, K1, R> =
    PartitionedCacheKey3x1(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, I2, I3, K1, K2> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape3x2<I1, I2, I3, K1, K2>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey3x2<I1, I2, I3, K1, K2, R> =
    PartitionedCacheKey3x2(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, I2, I3, K1, K2, K3> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape3x3<I1, I2, I3, K1, K2, K3>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey3x3<I1, I2, I3, K1, K2, K3, R> =
    PartitionedCacheKey3x3(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, I2, I3, I4, K1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape4x1<I1, I2, I3, I4, K1>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey4x1<I1, I2, I3, I4, K1, R> =
    PartitionedCacheKey4x1(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, I2, I3, I4, K1, K2> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape4x2<I1, I2, I3, I4, K1, K2>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey4x2<I1, I2, I3, I4, K1, K2, R> =
    PartitionedCacheKey4x2(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

fun <R, I1, I2, I3, I4, I5, K1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape5x1<I1, I2, I3, I4, I5, K1>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey5x1<I1, I2, I3, I4, I5, K1, R> =
    PartitionedCacheKey5x1(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

class ExactCacheKey0<R> @PublishedApi internal constructor(
    private val name: String,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheEntryRef<R> = invoke()

    operator fun invoke(): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
    )
}

class ExactCacheKey1<P1, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPart<P1>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(p1: P1): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(key.encodePart(p1)),
        partitionPartNames = listOf(key.name),
    )
}

class ExactCacheKey2<P1, P2, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPartComposition2<P1, P2>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(p1: P1, p2: P2): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = key.encodeParts(p1, p2),
        partitionPartNames = key.partNames(),
    )
}

class ExactCacheKey3<P1, P2, P3, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPartComposition3<P1, P2, P3>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(p1: P1, p2: P2, p3: P3): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = key.encodeParts(p1, p2, p3),
        partitionPartNames = key.partNames(),
    )
}

class ExactCacheKey4<P1, P2, P3, P4, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPartComposition4<P1, P2, P3, P4>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = key.encodeParts(p1, p2, p3, p4),
        partitionPartNames = key.partNames(),
    )
}

class ExactCacheKey5<P1, P2, P3, P4, P5, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = key.encodeParts(p1, p2, p3, p4, p5),
        partitionPartNames = key.partNames(),
    )
}

class ExactCacheKey6<P1, P2, P3, P4, P5, P6, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = key.encodeParts(p1, p2, p3, p4, p5, p6),
        partitionPartNames = key.partNames(),
    )
}

class SinglePartitionCacheKey1<K1, R> @PublishedApi internal constructor(
    private val name: String,
    private val itemKey: KeyPart<K1>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(k1: K1): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyPartArgs = listOf(itemKey.encodePart(k1)),
        itemKeyPartNames = listOf(itemKey.name),
    )

    fun partition(): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
    )

    fun matching(vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyParts = listOf(itemKey),
        selections = itemKeyParts,
    )
}

class SinglePartitionCacheKey2<K1, K2, R> @PublishedApi internal constructor(
    private val name: String,
    private val itemKey: KeyPartComposition2<K1, K2>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(k1: K1, k2: K2): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
    )

    fun matching(vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class SinglePartitionCacheKey3<K1, K2, K3, R> @PublishedApi internal constructor(
    private val name: String,
    private val itemKey: KeyPartComposition3<K1, K2, K3>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(k1: K1, k2: K2, k3: K3): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2, k3),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(): CachePartRef<R> = cachePartRef(name, plannedStorage, emptyList(), emptyList())

    fun matching(vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class SinglePartitionCacheKey4<K1, K2, K3, K4, R> @PublishedApi internal constructor(
    private val name: String,
    private val itemKey: KeyPartComposition4<K1, K2, K3, K4>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(k1: K1, k2: K2, k3: K3, k4: K4): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2, k3, k4),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(): CachePartRef<R> = cachePartRef(name, plannedStorage, emptyList(), emptyList())

    fun matching(vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class SinglePartitionCacheKey5<K1, K2, K3, K4, K5, R> @PublishedApi internal constructor(
    private val name: String,
    private val itemKey: KeyPartComposition5<K1, K2, K3, K4, K5>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(k1: K1, k2: K2, k3: K3, k4: K4, k5: K5): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2, k3, k4, k5),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(): CachePartRef<R> = cachePartRef(name, plannedStorage, emptyList(), emptyList())

    fun matching(vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class SinglePartitionCacheKey6<K1, K2, K3, K4, K5, K6, R> @PublishedApi internal constructor(
    private val name: String,
    private val itemKey: KeyPartComposition6<K1, K2, K3, K4, K5, K6>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(k1: K1, k2: K2, k3: K3, k4: K4, k5: K5, k6: K6): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2, k3, k4, k5, k6),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(): CachePartRef<R> = cachePartRef(name, plannedStorage, emptyList(), emptyList())

    fun matching(vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = emptyList(),
        partitionPartNames = emptyList(),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey1x1<I1, K1, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPart<I1>,
    private val itemKey: KeyPart<K1>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, k1: K1): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        itemKeyPartArgs = listOf(itemKey.encodePart(k1)),
        itemKeyPartNames = listOf(itemKey.name),
    )

    fun partition(i1: I1): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
    )

    fun matching(i1: I1, vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        itemKeyParts = listOf(itemKey),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey1x2<I1, K1, K2, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPart<I1>,
    private val itemKey: KeyPartComposition2<K1, K2>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, k1: K1, k2: K2): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(i1: I1): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
    )

    fun matching(i1: I1, vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey1x3<I1, K1, K2, K3, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPart<I1>,
    private val itemKey: KeyPartComposition3<K1, K2, K3>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, k1: K1, k2: K2, k3: K3): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2, k3),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(i1: I1): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
    )

    fun matching(i1: I1, vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey1x4<I1, K1, K2, K3, K4, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPart<I1>,
    private val itemKey: KeyPartComposition4<K1, K2, K3, K4>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, k1: K1, k2: K2, k3: K3, k4: K4): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2, k3, k4),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(i1: I1): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
    )

    fun matching(i1: I1, vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey1x5<I1, K1, K2, K3, K4, K5, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPart<I1>,
    private val itemKey: KeyPartComposition5<K1, K2, K3, K4, K5>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, k1: K1, k2: K2, k3: K3, k4: K4, k5: K5): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2, k3, k4, k5),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(i1: I1): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
    )

    fun matching(i1: I1, vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey2x1<I1, I2, K1, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition2<I1, I2>,
    private val itemKey: KeyPart<K1>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, i2: I2, k1: K1): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
        itemKeyPartArgs = listOf(itemKey.encodePart(k1)),
        itemKeyPartNames = listOf(itemKey.name),
    )

    fun partition(i1: I1, i2: I2): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
    )
}

class PartitionedCacheKey2x2<I1, I2, K1, K2, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition2<I1, I2>,
    private val itemKey: KeyPartComposition2<K1, K2>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, i2: I2, k1: K1, k2: K2): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(i1: I1, i2: I2): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
    )

    fun matching(i1: I1, i2: I2, vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey2x3<I1, I2, K1, K2, K3, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition2<I1, I2>,
    private val itemKey: KeyPartComposition3<K1, K2, K3>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, i2: I2, k1: K1, k2: K2, k3: K3): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2, k3),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(i1: I1, i2: I2): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
    )

    fun matching(i1: I1, i2: I2, vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey2x4<I1, I2, K1, K2, K3, K4, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition2<I1, I2>,
    private val itemKey: KeyPartComposition4<K1, K2, K3, K4>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, i2: I2, k1: K1, k2: K2, k3: K3, k4: K4): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2, k3, k4),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(i1: I1, i2: I2): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
    )

    fun matching(i1: I1, i2: I2, vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey3x1<I1, I2, I3, K1, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition3<I1, I2, I3>,
    private val itemKey: KeyPart<K1>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, i2: I2, i3: I3, k1: K1): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3),
        partitionPartNames = partition.partNames(),
        itemKeyPartArgs = listOf(itemKey.encodePart(k1)),
        itemKeyPartNames = listOf(itemKey.name),
    )

    fun partition(i1: I1, i2: I2, i3: I3): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3),
        partitionPartNames = partition.partNames(),
    )
}

class PartitionedCacheKey3x2<I1, I2, I3, K1, K2, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition3<I1, I2, I3>,
    private val itemKey: KeyPartComposition2<K1, K2>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, i2: I2, i3: I3, k1: K1, k2: K2): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3),
        partitionPartNames = partition.partNames(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(i1: I1, i2: I2, i3: I3): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3),
        partitionPartNames = partition.partNames(),
    )

    fun matching(i1: I1, i2: I2, i3: I3, vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3),
        partitionPartNames = partition.partNames(),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey3x3<I1, I2, I3, K1, K2, K3, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition3<I1, I2, I3>,
    private val itemKey: KeyPartComposition3<K1, K2, K3>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, i2: I2, i3: I3, k1: K1, k2: K2, k3: K3): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3),
        partitionPartNames = partition.partNames(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2, k3),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(i1: I1, i2: I2, i3: I3): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3),
        partitionPartNames = partition.partNames(),
    )
}

class PartitionedCacheKey4x1<I1, I2, I3, I4, K1, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition4<I1, I2, I3, I4>,
    private val itemKey: KeyPart<K1>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, i2: I2, i3: I3, i4: I4, k1: K1): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3, i4),
        partitionPartNames = partition.partNames(),
        itemKeyPartArgs = listOf(itemKey.encodePart(k1)),
        itemKeyPartNames = listOf(itemKey.name),
    )

    fun partition(i1: I1, i2: I2, i3: I3, i4: I4): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3, i4),
        partitionPartNames = partition.partNames(),
    )
}

class PartitionedCacheKey4x2<I1, I2, I3, I4, K1, K2, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition4<I1, I2, I3, I4>,
    private val itemKey: KeyPartComposition2<K1, K2>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, i2: I2, i3: I3, i4: I4, k1: K1, k2: K2): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3, i4),
        partitionPartNames = partition.partNames(),
        itemKeyPartArgs = itemKey.encodeParts(k1, k2),
        itemKeyPartNames = itemKey.partNames(),
    )

    fun partition(i1: I1, i2: I2, i3: I3, i4: I4): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3, i4),
        partitionPartNames = partition.partNames(),
    )

    fun matching(i1: I1, i2: I2, i3: I3, i4: I4, vararg itemKeyParts: MatchableKeyPartValue): CachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3, i4),
        partitionPartNames = partition.partNames(),
        itemKeyParts = itemKey.parts(),
        selections = itemKeyParts,
    )
}

class PartitionedCacheKey5x1<I1, I2, I3, I4, I5, K1, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition5<I1, I2, I3, I4, I5>,
    private val itemKey: KeyPart<K1>,
    private val plannedStorage: PlannedStorage<R>,
) {
    fun all(): CacheAllRef<R> = cacheAllRef(name, plannedStorage)

    operator fun invoke(i1: I1, i2: I2, i3: I3, i4: I4, i5: I5, k1: K1): CacheEntryRef<R> = cacheEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3, i4, i5),
        partitionPartNames = partition.partNames(),
        itemKeyPartArgs = listOf(itemKey.encodePart(k1)),
        itemKeyPartNames = listOf(itemKey.name),
    )

    fun partition(i1: I1, i2: I2, i3: I3, i4: I4, i5: I5): CachePartRef<R> = cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3, i4, i5),
        partitionPartNames = partition.partNames(),
    )
}

private fun <R> matchingEntryParts(
    name: String,
    plannedStorage: PlannedStorage<R>,
    partitionPartArgs: List<CacheArgs>,
    partitionPartNames: List<String?>,
    itemKeyParts: List<KeyPart<*>>,
    selections: Array<out MatchableKeyPartValue>,
): CachePartRef<R> {
    require(selections.isNotEmpty()) { "matching(...) requires at least one matchable entry-key part." }

    val matchableNames = itemKeyParts
        .filter { it.isMatchable() }
        .map { part ->
            requireNotNull(part.name) {
                "Matchable entry-key parts must be named. Use keyPart<T>(\"name\") or delegated key parts."
            }
        }
        .toSet()
    require(matchableNames.isNotEmpty()) { "This cache key does not declare matchable entry-key parts." }

    val selectionsByName = mutableMapOf<String, MatchableKeyPartValue>()
    selections.forEach { selection ->
        val selectionName = requireNotNull(selection.name) {
            "matching(...) requires named key parts. Use keyPart<T>(\"name\") or delegated key parts."
        }
        require(selectionName in matchableNames) {
            "Entry-key part $selectionName is not matchable for this cache key."
        }
        require(selectionsByName.put(selectionName, selection) == null) {
            "Entry-key part $selectionName was selected more than once."
        }
    }

    val patternPartArgs = itemKeyParts.map { part ->
        selectionsByName[part.name]?.args ?: part.wildcardArgs()
    }

    return cachePartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partitionPartArgs,
        partitionPartNames = partitionPartNames,
        itemKeyPatternPartArgs = patternPartArgs,
    )
}

@PublishedApi
internal fun KeyPart<*>.isMatchable(): Boolean = this is MatchableKeyPart<*>

private fun KeyPart<*>.wildcardArgs(): CacheArgs {
    val segmentCount = requireNotNull(segmentCount) {
        "matching(...) requires fixed-size key parts. rawKeyPart() cannot be used here."
    }
    return argsOf(*Array(segmentCount) { CachePatternWildcard })
}

@PublishedApi
internal fun KeyPartComposition2<*, *>.parts(): List<KeyPart<*>> = listOf(first, second)

@PublishedApi
internal fun KeyPartComposition3<*, *, *>.parts(): List<KeyPart<*>> = listOf(first, second, third)

@PublishedApi
internal fun KeyPartComposition4<*, *, *, *>.parts(): List<KeyPart<*>> = listOf(first, second, third, fourth)

@PublishedApi
internal fun KeyPartComposition5<*, *, *, *, *>.parts(): List<KeyPart<*>> = listOf(first, second, third, fourth, fifth)

@PublishedApi
internal fun KeyPartComposition6<*, *, *, *, *, *>.parts(): List<KeyPart<*>> = listOf(first, second, third, fourth, fifth, sixth)

/**
 * Caches a typed cache entry, returning cached data when present or [block]'s result otherwise.
 *
 * [cacheIf] is evaluated only for newly computed results.
 */
suspend operator fun <R> Kacheable.invoke(
    entryRef: CacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R = invoke(entryRef.entryRef, entryRef.returnView, cacheIf, block)

/**
 * Caches a typed cache entry using an explicit miss policy.
 */
suspend operator fun <R> Kacheable.invoke(
    entryRef: CacheEntryRef<R>,
    missPolicy: CacheMissPolicy<R>,
    block: suspend () -> R,
): R = invoke(entryRef.entryRef, entryRef.returnView, missPolicy, block)

/**
 * Caches a typed cache entry using an explicit miss policy and store-result predicate.
 */
suspend operator fun <R> Kacheable.invoke(
    entryRef: CacheEntryRef<R>,
    missPolicy: CacheMissPolicy<R>,
    storeResultIf: (R) -> Boolean,
    block: suspend () -> R,
): R = invoke(
    entryRef.entryRef,
    entryRef.returnView,
    missPolicy,
    CacheRefreshPolicy.neverRefresh(),
    storeResultIf,
) { block() }

/**
 * Caches a typed cache entry using explicit miss, refresh, and store-result policies. The loader
 * receives the previous cached value on refresh and `null` on a true miss.
 */
suspend operator fun <R> Kacheable.invoke(
    entryRef: CacheEntryRef<R>,
    missPolicy: CacheMissPolicy<R>,
    refreshPolicy: CacheRefreshPolicy<R>,
    storeResultIf: (R) -> Boolean,
    block: suspend (previous: R?) -> R,
): R = invoke(entryRef.entryRef, entryRef.returnView, missPolicy, refreshPolicy, storeResultIf, block)

/**
 * Named equivalent of invoking a typed cache entry.
 */
suspend fun <R> Kacheable.cache(
    entryRef: CacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R = invoke(entryRef, cacheIf, block)

/**
 * Named equivalent of invoking a typed cache entry with an explicit miss policy.
 */
suspend fun <R> Kacheable.cache(
    entryRef: CacheEntryRef<R>,
    missPolicy: CacheMissPolicy<R>,
    block: suspend () -> R,
): R = invoke(entryRef, missPolicy, block)

/**
 * Named equivalent of invoking a typed cache entry with an explicit miss policy and store-result
 * predicate.
 */
suspend fun <R> Kacheable.cache(
    entryRef: CacheEntryRef<R>,
    missPolicy: CacheMissPolicy<R>,
    storeResultIf: (R) -> Boolean,
    block: suspend () -> R,
): R = invoke(entryRef, missPolicy, storeResultIf, block)

/**
 * Named equivalent of invoking a typed cache entry with explicit miss, refresh, and store-result
 * policies. The loader receives the previous cached value on refresh and `null` on a true miss.
 */
suspend fun <R> Kacheable.cache(
    entryRef: CacheEntryRef<R>,
    missPolicy: CacheMissPolicy<R>,
    refreshPolicy: CacheRefreshPolicy<R>,
    storeResultIf: (R) -> Boolean,
    block: suspend (previous: R?) -> R,
): R = invoke(entryRef, missPolicy, refreshPolicy, storeResultIf, block)

/**
 * Invalidates one or more typed cache entries.
 */
suspend fun Kacheable.invalidate(vararg entryRefs: CacheEntryRef<*>) {
    entryRefs.forEach { invalidateCacheRef(it) }
}

suspend fun <R> Kacheable.invalidate(
    vararg entryRefs: CacheEntryRef<*>,
    block: suspend () -> R,
): R {
    val result = block()
    invalidate(*entryRefs)
    return result
}

suspend fun Kacheable.invalidate(vararg partRefs: CachePartRef<*>) {
    partRefs.forEach { invalidateCacheRef(it) }
}

suspend fun <R> Kacheable.invalidate(
    vararg partRefs: CachePartRef<*>,
    block: suspend () -> R,
): R {
    val result = block()
    invalidate(*partRefs)
    return result
}

suspend fun Kacheable.invalidate(vararg refs: CacheInvalidationRef) {
    refs.forEach { invalidateCacheRef(it) }
}

suspend fun Kacheable.invalidate(refs: Iterable<CacheInvalidationRef>) {
    refs.forEach { invalidateCacheRef(it) }
}

suspend fun <R> Kacheable.invalidate(
    vararg refs: CacheInvalidationRef,
    block: suspend () -> R,
): R {
    val result = block()
    invalidate(*refs)
    return result
}

suspend fun <R> Kacheable.invalidate(
    refs: Iterable<CacheInvalidationRef>,
    block: suspend () -> R,
): R {
    val result = block()
    invalidate(refs)
    return result
}

@Suppress("UNCHECKED_CAST")
private suspend fun Kacheable.invalidateCacheRef(entryRef: CacheEntryRef<*>) {
    val returnView = entryRef.returnView
    if (entryRef.entryRef.storage == CacheStorage.Set && returnView is EnumMemberCacheReturn<*>) {
        invalidate(entryRef.entryRef as StoredCacheEntryRef<CacheStorage.Set>, returnView as EnumMemberCacheReturn<Any>)
    } else {
        invalidate(entryRef.entryRef)
    }
}

@Suppress("UNCHECKED_CAST")
private suspend fun Kacheable.invalidateCacheRef(partRef: CachePartRef<*>) {
    val returnView = partRef.returnView
    if (partRef.partRef.storage == CacheStorage.Set && returnView is EnumMemberCacheReturn<*>) {
        invalidate(partRef.partRef as StoredCachePartRef<CacheStorage.Set>, returnView as EnumMemberCacheReturn<Any>)
    } else {
        invalidate(partRef.partRef)
    }
}

private suspend fun Kacheable.invalidateCacheRef(allRef: CacheAllRef<*>) {
    invalidate(allRef.allRef)
}

private suspend fun Kacheable.invalidateCacheRef(ref: CacheInvalidationRef) {
    when (ref) {
        is RawCacheEntryRef -> invalidate(ref.entryRef)
        is RawCacheRef -> invalidate(ref.allRef)
        is CacheEntryRef<*> -> invalidateCacheRef(ref)
        is CachePartRef<*> -> invalidateCacheRef(ref)
        is CacheAllRef<*> -> invalidateCacheRef(ref)
    }
}
