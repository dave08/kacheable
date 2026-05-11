@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.TypedCacheRuntime
import com.github.dave08.kacheable.internal.keys.cacheArgs
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

@ExperimentalKacheableApi
sealed interface ExactStoragePlan<out R>

@ExperimentalKacheableApi
sealed interface IndexedStoragePlan<out R>

@ExperimentalKacheableApi
data object AutoStoragePlan : ExactStoragePlan<Nothing>, IndexedStoragePlan<Nothing>

@ExperimentalKacheableApi
class ExactValueStoragePlan<R> internal constructor() : ExactStoragePlan<R>

@ExperimentalKacheableApi
class IndexedValueStoragePlan<R> internal constructor() : IndexedStoragePlan<R>

@ExperimentalKacheableApi
class MembershipStoragePlan internal constructor(
    val cacheFalse: Boolean,
) : IndexedStoragePlan<Boolean>

@ExperimentalKacheableApi
class EnumMembershipStoragePlan<E : Enum<E>> @PublishedApi internal constructor(
    val returnView: EnumMemberCacheReturn<E>,
) : IndexedStoragePlan<E>

@ExperimentalKacheableApi
fun auto(): AutoStoragePlan = AutoStoragePlan

@ExperimentalKacheableApi
fun <R> exactValueStorage(): ExactValueStoragePlan<R> = ExactValueStoragePlan()

@ExperimentalKacheableApi
fun <R> indexedValueStorage(): IndexedValueStoragePlan<R> = IndexedValueStoragePlan()

@ExperimentalKacheableApi
fun membershipStorage(cacheFalse: Boolean = true): MembershipStoragePlan = MembershipStoragePlan(cacheFalse)

@ExperimentalKacheableApi
inline fun <reified E : Enum<E>> enumMembershipStorage(
    values: List<E> = enumValues<E>().toList(),
    noinline valueName: (E) -> String = { it.name },
): EnumMembershipStoragePlan<E> = EnumMembershipStoragePlan(EnumMemberCacheReturn(values, valueName, serializer<E>()))

@ExperimentalKacheableApi
sealed interface CacheInvalidationRef

@ExperimentalKacheableApi
class RawCacheEntryRef internal constructor(
    internal val entryRef: StoredCacheEntryRef<CacheStorage.String>,
) : CacheInvalidationRef {
    override fun toString(): String = entryRef.toDebugString()
}

@ExperimentalKacheableApi
class RawCacheRef internal constructor(
    internal val allRef: StoredCacheAllRef<CacheStorage.String>,
) : CacheInvalidationRef {
    override fun toString(): String = allRef.toDebugString()
}

@ExperimentalKacheableApi
class CacheEntryRef<R> internal constructor(
    internal val entryRef: StoredCacheEntryRef<CacheStorage>,
    internal val returnView: CacheReturn<R, *>,
) : CacheInvalidationRef {
    override fun toString(): String = entryRef.toDebugString()
}

@ExperimentalKacheableApi
class CachePartRef<R> internal constructor(
    internal val partRef: StoredCachePartRef<CacheStorage>,
    internal val returnView: CacheReturn<R, *>,
) : CacheInvalidationRef {
    override fun toString(): String = partRef.toDebugString()
}

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
fun rawCache(
    cacheName: String,
): RawCacheRef = RawCacheRef(StoredAllRef(cacheName, CacheStorage.String))

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
inline fun <reified R> returns(): CacheResult<R> = cacheResult()

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
interface MatchableKeyPart<P> : KeyPart<P> {
    override fun invoke(value: P): MatchableKeyPartValue = MatchableKeyPartValue(this, encode(value))
}

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
fun <P> matchableKeyPart(name: String): MatchableKeyPart<P> =
    SimpleMatchableKeyPart(keyPart(name))

@ExperimentalKacheableApi
fun <P> matchableKeyPart(
    name: String,
    vararg values: (P) -> Any?,
): MatchableKeyPart<P> = SimpleMatchableKeyPart(keyPart(name, *values))

@ExperimentalKacheableApi
sealed interface ExactKeyShape

@ExperimentalKacheableApi
sealed interface PartitionedKeyShape {
    val hasMatchableEntryParts: Boolean
}

@ExperimentalKacheableApi
data object ExactKeyShape0 : ExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape1<P1> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPart<P1>,
) : ExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape2<P1, P2> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition2<P1, P2>,
) : ExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape3<P1, P2, P3> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition3<P1, P2, P3>,
) : ExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape4<P1, P2, P3, P4> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition4<P1, P2, P3, P4>,
) : ExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape5<P1, P2, P3, P4, P5> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
) : ExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape6<P1, P2, P3, P4, P5, P6> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
) : ExactKeyShape

@ExperimentalKacheableApi
class PartitionedKeyShape1x1<I1, K1> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPart<I1>,
    @PublishedApi internal val itemKey: KeyPart<K1>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.isMatchable()
}

@ExperimentalKacheableApi
class SinglePartitionKeyShape1<K1> @PublishedApi internal constructor(
    @PublishedApi internal val itemKey: KeyPart<K1>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.isMatchable()
}

@ExperimentalKacheableApi
class SinglePartitionKeyShape2<K1, K2> @PublishedApi internal constructor(
    @PublishedApi internal val itemKey: KeyPartComposition2<K1, K2>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

@ExperimentalKacheableApi
class PartitionedKeyShape1x2<I1, K1, K2> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPart<I1>,
    @PublishedApi internal val itemKey: KeyPartComposition2<K1, K2>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

@ExperimentalKacheableApi
class PartitionedKeyShape2x1<I1, I2, K1> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition2<I1, I2>,
    @PublishedApi internal val itemKey: KeyPart<K1>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.isMatchable()
}

@ExperimentalKacheableApi
class PartitionedKeyShape2x3<I1, I2, K1, K2, K3> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition2<I1, I2>,
    @PublishedApi internal val itemKey: KeyPartComposition3<K1, K2, K3>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

@ExperimentalKacheableApi
class PartitionedKeyShape3x3<I1, I2, I3, K1, K2, K3> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition3<I1, I2, I3>,
    @PublishedApi internal val itemKey: KeyPartComposition3<K1, K2, K3>,
) : PartitionedKeyShape {
    override val hasMatchableEntryParts: Boolean = itemKey.parts().any { it.isMatchable() }
}

@ExperimentalKacheableApi
fun exact(): ExactKeyShape0 = ExactKeyShape0

@ExperimentalKacheableApi
fun <P1> exact(
    key: KeyPart<P1>,
): ExactKeyShape1<P1> = ExactKeyShape1(key)

@ExperimentalKacheableApi
fun <P1, P2> exact(
    key: KeyPartComposition2<P1, P2>,
): ExactKeyShape2<P1, P2> = ExactKeyShape2(key)

@ExperimentalKacheableApi
fun <P1, P2, P3> exact(
    key: KeyPartComposition3<P1, P2, P3>,
): ExactKeyShape3<P1, P2, P3> = ExactKeyShape3(key)

@ExperimentalKacheableApi
fun <P1, P2, P3, P4> exact(
    key: KeyPartComposition4<P1, P2, P3, P4>,
): ExactKeyShape4<P1, P2, P3, P4> = ExactKeyShape4(key)

@ExperimentalKacheableApi
fun <P1, P2, P3, P4, P5> exact(
    key: KeyPartComposition5<P1, P2, P3, P4, P5>,
): ExactKeyShape5<P1, P2, P3, P4, P5> = ExactKeyShape5(key)

@ExperimentalKacheableApi
fun <P1, P2, P3, P4, P5, P6> exact(
    key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
): ExactKeyShape6<P1, P2, P3, P4, P5, P6> = ExactKeyShape6(key)

@ExperimentalKacheableApi
fun <I1, K1> partitioned(
    partition: KeyPart<I1>,
    key: KeyPart<K1>,
): PartitionedKeyShape1x1<I1, K1> = PartitionedKeyShape1x1(partition, key)

@ExperimentalKacheableApi
fun <K1> partitioned(
    key: KeyPart<K1>,
): SinglePartitionKeyShape1<K1> = SinglePartitionKeyShape1(key)

@ExperimentalKacheableApi
fun <K1, K2> partitioned(
    key: KeyPartComposition2<K1, K2>,
): SinglePartitionKeyShape2<K1, K2> = SinglePartitionKeyShape2(key)

@ExperimentalKacheableApi
fun <I1, K1, K2> partitioned(
    partition: KeyPart<I1>,
    key: KeyPartComposition2<K1, K2>,
): PartitionedKeyShape1x2<I1, K1, K2> = PartitionedKeyShape1x2(partition, key)

@ExperimentalKacheableApi
fun <I1, I2, K1> partitioned(
    partition: KeyPartComposition2<I1, I2>,
    key: KeyPart<K1>,
): PartitionedKeyShape2x1<I1, I2, K1> = PartitionedKeyShape2x1(partition, key)

@ExperimentalKacheableApi
fun <I1, I2, K1, K2, K3> partitioned(
    partition: KeyPartComposition2<I1, I2>,
    key: KeyPartComposition3<K1, K2, K3>,
): PartitionedKeyShape2x3<I1, I2, K1, K2, K3> = PartitionedKeyShape2x3(partition, key)

@ExperimentalKacheableApi
fun <I1, I2, I3, K1, K2, K3> partitioned(
    partition: KeyPartComposition3<I1, I2, I3>,
    key: KeyPartComposition3<K1, K2, K3>,
): PartitionedKeyShape3x3<I1, I2, I3, K1, K2, K3> = PartitionedKeyShape3x3(partition, key)

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

@ExperimentalKacheableApi
fun <R> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape0,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey0<R> = ExactCacheKey0(name, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape1<P1>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey1<P1, R> = ExactCacheKey1(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1, P2> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape2<P1, P2>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey2<P1, P2, R> = ExactCacheKey2(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1, P2, P3> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape3<P1, P2, P3>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey3<P1, P2, P3, R> = ExactCacheKey3(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1, P2, P3, P4> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape4<P1, P2, P3, P4>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey4<P1, P2, P3, P4, R> = ExactCacheKey4(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1, P2, P3, P4, P5> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape5<P1, P2, P3, P4, P5>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey5<P1, P2, P3, P4, P5, R> = ExactCacheKey5(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1, P2, P3, P4, P5, P6> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: ExactKeyShape6<P1, P2, P3, P4, P5, P6>,
    storage: ExactStoragePlan<R> = auto(),
): ExactCacheKey6<P1, P2, P3, P4, P5, P6, R> = ExactCacheKey6(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, I1, K1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape1x1<I1, K1>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey1x1<I1, K1, R> =
    PartitionedCacheKey1x1(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
fun <R, K1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: SinglePartitionKeyShape1<K1>,
    storage: IndexedStoragePlan<R> = auto(),
): SinglePartitionCacheKey1<K1, R> =
    SinglePartitionCacheKey1(name, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
fun <R, K1, K2> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: SinglePartitionKeyShape2<K1, K2>,
    storage: IndexedStoragePlan<R> = auto(),
): SinglePartitionCacheKey2<K1, K2, R> =
    SinglePartitionCacheKey2(name, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
fun <R, I1, K1, K2> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape1x2<I1, K1, K2>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey1x2<I1, K1, K2, R> =
    PartitionedCacheKey1x2(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
fun <R, I1, I2, K1> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape2x1<I1, I2, K1>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey2x1<I1, I2, K1, R> =
    PartitionedCacheKey2x1(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
fun <R, I1, I2, K1, K2, K3> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape2x3<I1, I2, K1, K2, K3>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey2x3<I1, I2, K1, K2, K3, R> =
    PartitionedCacheKey2x3(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
fun <R, I1, I2, I3, K1, K2, K3> cacheKey(
    name: String,
    returns: CacheResult<R>,
    key: PartitionedKeyShape3x3<I1, I2, I3, K1, K2, K3>,
    storage: IndexedStoragePlan<R> = auto(),
): PartitionedCacheKey3x3<I1, I2, I3, K1, K2, K3, R> =
    PartitionedCacheKey3x3(name, key.partition, key.itemKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
suspend operator fun <R> Kacheable.invoke(
    entryRef: CacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R = (this as TypedCacheRuntime).invoke(entryRef.entryRef, entryRef.returnView, cacheIf, block)

@ExperimentalKacheableApi
suspend fun <R> Kacheable.cache(
    entryRef: CacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R = invoke(entryRef, cacheIf, block)

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg entryRefs: CacheEntryRef<*>) {
    val runtime = this as TypedCacheRuntime
    entryRefs.forEach { runtime.invalidateCacheRef(it) }
}

@ExperimentalKacheableApi
suspend fun <R> Kacheable.invalidate(
    vararg entryRefs: CacheEntryRef<*>,
    block: suspend () -> R,
): R {
    val result = block()
    invalidate(*entryRefs)
    return result
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg partRefs: CachePartRef<*>) {
    val runtime = this as TypedCacheRuntime
    partRefs.forEach { runtime.invalidateCacheRef(it) }
}

@ExperimentalKacheableApi
suspend fun <R> Kacheable.invalidate(
    vararg partRefs: CachePartRef<*>,
    block: suspend () -> R,
): R {
    val result = block()
    invalidate(*partRefs)
    return result
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg refs: CacheInvalidationRef) {
    val runtime = this as TypedCacheRuntime
    refs.forEach { runtime.invalidateCacheRef(it) }
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(refs: Iterable<CacheInvalidationRef>) {
    val runtime = this as TypedCacheRuntime
    refs.forEach { runtime.invalidateCacheRef(it) }
}

@ExperimentalKacheableApi
suspend fun <R> Kacheable.invalidate(
    vararg refs: CacheInvalidationRef,
    block: suspend () -> R,
): R {
    val result = block()
    invalidate(*refs)
    return result
}

@ExperimentalKacheableApi
suspend fun <R> Kacheable.invalidate(
    refs: Iterable<CacheInvalidationRef>,
    block: suspend () -> R,
): R {
    val result = block()
    invalidate(refs)
    return result
}

@Suppress("UNCHECKED_CAST")
private suspend fun TypedCacheRuntime.invalidateCacheRef(entryRef: CacheEntryRef<*>) {
    val returnView = entryRef.returnView
    if (entryRef.entryRef.storage == CacheStorage.Set && returnView is EnumMemberCacheReturn<*>) {
        invalidate(entryRef.entryRef as StoredCacheEntryRef<CacheStorage.Set>, returnView as EnumMemberCacheReturn<Any>)
    } else {
        invalidate(entryRef.entryRef)
    }
}

@Suppress("UNCHECKED_CAST")
private suspend fun TypedCacheRuntime.invalidateCacheRef(partRef: CachePartRef<*>) {
    val returnView = partRef.returnView
    if (partRef.partRef.storage == CacheStorage.Set && returnView is EnumMemberCacheReturn<*>) {
        invalidate(partRef.partRef as StoredCachePartRef<CacheStorage.Set>, returnView as EnumMemberCacheReturn<Any>)
    } else {
        invalidate(partRef.partRef)
    }
}

private suspend fun TypedCacheRuntime.invalidateCacheRef(allRef: CacheAllRef<*>) {
    invalidate(allRef.allRef)
}

private suspend fun TypedCacheRuntime.invalidateCacheRef(ref: CacheInvalidationRef) {
    when (ref) {
        is RawCacheEntryRef -> invalidate(ref.entryRef)
        is RawCacheRef -> invalidate(ref.allRef)
        is CacheEntryRef<*> -> invalidateCacheRef(ref)
        is CachePartRef<*> -> invalidateCacheRef(ref)
        is CacheAllRef<*> -> invalidateCacheRef(ref)
    }
}
