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
    val returnsAs: EnumMemberCacheReturn<E>,
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
class LogicalCacheEntryRef<R> internal constructor(
    internal val entryRef: StoredCacheEntryRef<CacheStorage>,
    internal val returnsAs: CacheReturn<R, *>,
) : CacheInvalidationRef

@ExperimentalKacheableApi
class LogicalCachePartRef<R> internal constructor(
    internal val partRef: StoredCachePartRef<CacheStorage>,
    internal val returnsAs: CacheReturn<R, *>,
) : CacheInvalidationRef

@ExperimentalKacheableApi
class LogicalCacheResult<R> @PublishedApi internal constructor(
    @PublishedApi internal val resultClass: KClass<*>,
    @PublishedApi internal val isNullable: Boolean,
    @PublishedApi internal val valueReturn: ValueCacheReturn<R>,
    @PublishedApi internal val enumReturn: EnumMemberCacheReturn<*>?,
)

@PublishedApi
internal data class PlannedStorage<R>(
    val storage: CacheStorage,
    val returnsAs: CacheReturn<R, *>,
)

@PublishedApi
internal inline fun <reified R> logicalCacheResult(): LogicalCacheResult<R> =
    LogicalCacheResult(
        resultClass = typeOf<R>().classifier as KClass<*>,
        isNullable = typeOf<R>().isMarkedNullable,
        valueReturn = value<R>(),
        enumReturn = enumMemberReturnOrNull<R>(),
    )

@ExperimentalKacheableApi
inline fun <reified R> returns(): LogicalCacheResult<R> = logicalCacheResult()

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
sealed interface LogicalExactKeyShape

@ExperimentalKacheableApi
sealed interface LogicalIndexedKeyShape {
    val hasMatchableEntryParts: Boolean
}

@ExperimentalKacheableApi
class ExactKeyShape1<P1> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPart<P1>,
) : LogicalExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape2<P1, P2> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition2<P1, P2>,
) : LogicalExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape3<P1, P2, P3> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition3<P1, P2, P3>,
) : LogicalExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape4<P1, P2, P3, P4> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition4<P1, P2, P3, P4>,
) : LogicalExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape5<P1, P2, P3, P4, P5> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
) : LogicalExactKeyShape

@ExperimentalKacheableApi
class ExactKeyShape6<P1, P2, P3, P4, P5, P6> @PublishedApi internal constructor(
    @PublishedApi internal val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
) : LogicalExactKeyShape

@ExperimentalKacheableApi
class PartitionedKeyShape1x1<I1, K1> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPart<I1>,
    @PublishedApi internal val entryKey: KeyPart<K1>,
) : LogicalIndexedKeyShape {
    override val hasMatchableEntryParts: Boolean = entryKey.isMatchable()
}

@ExperimentalKacheableApi
class PartitionedKeyShape1x2<I1, K1, K2> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPart<I1>,
    @PublishedApi internal val entryKey: KeyPartComposition2<K1, K2>,
) : LogicalIndexedKeyShape {
    override val hasMatchableEntryParts: Boolean = entryKey.parts().any { it.isMatchable() }
}

@ExperimentalKacheableApi
class PartitionedKeyShape2x1<I1, I2, K1> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition2<I1, I2>,
    @PublishedApi internal val entryKey: KeyPart<K1>,
) : LogicalIndexedKeyShape {
    override val hasMatchableEntryParts: Boolean = entryKey.isMatchable()
}

@ExperimentalKacheableApi
class PartitionedKeyShape2x3<I1, I2, K1, K2, K3> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition2<I1, I2>,
    @PublishedApi internal val entryKey: KeyPartComposition3<K1, K2, K3>,
) : LogicalIndexedKeyShape {
    override val hasMatchableEntryParts: Boolean = entryKey.parts().any { it.isMatchable() }
}

@ExperimentalKacheableApi
class PartitionedKeyShape3x3<I1, I2, I3, K1, K2, K3> @PublishedApi internal constructor(
    @PublishedApi internal val partition: KeyPartComposition3<I1, I2, I3>,
    @PublishedApi internal val entryKey: KeyPartComposition3<K1, K2, K3>,
) : LogicalIndexedKeyShape {
    override val hasMatchableEntryParts: Boolean = entryKey.parts().any { it.isMatchable() }
}

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
    result: LogicalCacheResult<R>,
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
    result: LogicalCacheResult<R>,
    storage: IndexedStoragePlan<R>,
    hasMatchableEntryParts: Boolean,
): PlannedStorage<R> =
    when (storage) {
        is AutoStoragePlan -> when {
            hasMatchableEntryParts -> PlannedStorage(CacheStorage.HashMap, result.valueReturn)
            !result.isNullable && result.resultClass == Boolean::class -> PlannedStorage(CacheStorage.Set, isMember() as CacheReturn<R, *>)
            !result.isNullable && result.enumReturn != null -> PlannedStorage(CacheStorage.Set, result.enumReturn as CacheReturn<R, *>)
            else -> PlannedStorage(CacheStorage.HashMap, result.valueReturn)
        }
        is IndexedValueStoragePlan<R> -> PlannedStorage(CacheStorage.HashMap, result.valueReturn)
        is MembershipStoragePlan -> PlannedStorage(CacheStorage.Set, isMember(storage.cacheFalse) as CacheReturn<R, *>)
        is EnumMembershipStoragePlan<*> -> PlannedStorage(CacheStorage.Set, storage.returnsAs as CacheReturn<R, *>)
    }

@PublishedApi
internal inline fun <reified R : Any> enumMemberReturn(): EnumMemberCacheReturn<R> {
    val values = requireNotNull(R::class.java.enumConstants?.toList()) {
        "Enum membership storage requires an enum result type."
    }
    return EnumMemberCacheReturn(
        values = values,
        valueName = { value -> (value as Enum<*>).name },
        serializer = serializer<R>(),
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
        serializer = serializer<R>() as kotlinx.serialization.KSerializer<Any>,
    )
}

private fun entryRef(
    name: String,
    storage: CacheStorage,
    partitionPartArgs: List<CacheArgs>,
    partitionPartNames: List<String?>,
    entryKeyPartArgs: List<CacheArgs> = emptyList(),
    entryKeyPartNames: List<String?> = emptyList(),
): StoredCacheEntryRef<CacheStorage> = CacheEntryRef(
    name = name,
    cacheArgs = cacheArgs(
        primaryPartArgs = partitionPartArgs,
        primaryPartNames = partitionPartNames,
        secondaryPartArgs = entryKeyPartArgs,
        secondaryPartNames = entryKeyPartNames,
    ),
    storage = storage,
)

private fun partRef(
    name: String,
    storage: CacheStorage,
    partitionPartArgs: List<CacheArgs>,
    partitionPartNames: List<String?>,
    entryKeyPatternPartArgs: List<CacheArgs>? = null,
): StoredCachePartRef<CacheStorage> = CachePartRef(
    name = name,
    args = joinArgs(*partitionPartArgs.toTypedArray()),
    cacheArgs = cacheArgs(
        primaryPartArgs = partitionPartArgs,
        primaryPartNames = partitionPartNames,
    ),
    storage = storage,
    secondaryPatternPartArgs = entryKeyPatternPartArgs,
)

private fun <R> logicalEntryRef(
    name: String,
    plannedStorage: PlannedStorage<R>,
    partitionPartArgs: List<CacheArgs>,
    partitionPartNames: List<String?>,
    entryKeyPartArgs: List<CacheArgs> = emptyList(),
    entryKeyPartNames: List<String?> = emptyList(),
): LogicalCacheEntryRef<R> = LogicalCacheEntryRef(
    entryRef = entryRef(
        name = name,
        storage = plannedStorage.storage,
        partitionPartArgs = partitionPartArgs,
        partitionPartNames = partitionPartNames,
        entryKeyPartArgs = entryKeyPartArgs,
        entryKeyPartNames = entryKeyPartNames,
    ),
    returnsAs = plannedStorage.returnsAs,
)

private fun <R> logicalPartRef(
    name: String,
    plannedStorage: PlannedStorage<R>,
    partitionPartArgs: List<CacheArgs>,
    partitionPartNames: List<String?>,
    entryKeyPatternPartArgs: List<CacheArgs>? = null,
): LogicalCachePartRef<R> = LogicalCachePartRef(
    partRef = partRef(
        name = name,
        storage = plannedStorage.storage,
        partitionPartArgs = partitionPartArgs,
        partitionPartNames = partitionPartNames,
        entryKeyPatternPartArgs = entryKeyPatternPartArgs,
    ),
    returnsAs = plannedStorage.returnsAs,
)

@ExperimentalKacheableApi
fun <R, P1> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: ExactKeyShape1<P1>,
    storage: ExactStoragePlan<R> = auto(),
): LogicalExactCacheKey1<P1, R> = LogicalExactCacheKey1(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1, P2> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: ExactKeyShape2<P1, P2>,
    storage: ExactStoragePlan<R> = auto(),
): LogicalExactCacheKey2<P1, P2, R> = LogicalExactCacheKey2(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1, P2, P3> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: ExactKeyShape3<P1, P2, P3>,
    storage: ExactStoragePlan<R> = auto(),
): LogicalExactCacheKey3<P1, P2, P3, R> = LogicalExactCacheKey3(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1, P2, P3, P4> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: ExactKeyShape4<P1, P2, P3, P4>,
    storage: ExactStoragePlan<R> = auto(),
): LogicalExactCacheKey4<P1, P2, P3, P4, R> = LogicalExactCacheKey4(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1, P2, P3, P4, P5> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: ExactKeyShape5<P1, P2, P3, P4, P5>,
    storage: ExactStoragePlan<R> = auto(),
): LogicalExactCacheKey5<P1, P2, P3, P4, P5, R> = LogicalExactCacheKey5(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, P1, P2, P3, P4, P5, P6> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: ExactKeyShape6<P1, P2, P3, P4, P5, P6>,
    storage: ExactStoragePlan<R> = auto(),
): LogicalExactCacheKey6<P1, P2, P3, P4, P5, P6, R> = LogicalExactCacheKey6(name, key.key, planExact(returns, storage))

@ExperimentalKacheableApi
fun <R, I1, K1> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: PartitionedKeyShape1x1<I1, K1>,
    storage: IndexedStoragePlan<R> = auto(),
): LogicalIndexedCacheKey1x1<I1, K1, R> =
    LogicalIndexedCacheKey1x1(name, key.partition, key.entryKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
fun <R, I1, K1, K2> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: PartitionedKeyShape1x2<I1, K1, K2>,
    storage: IndexedStoragePlan<R> = auto(),
): LogicalIndexedCacheKey1x2<I1, K1, K2, R> =
    LogicalIndexedCacheKey1x2(name, key.partition, key.entryKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
fun <R, I1, I2, K1> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: PartitionedKeyShape2x1<I1, I2, K1>,
    storage: IndexedStoragePlan<R> = auto(),
): LogicalIndexedCacheKey2x1<I1, I2, K1, R> =
    LogicalIndexedCacheKey2x1(name, key.partition, key.entryKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
fun <R, I1, I2, K1, K2, K3> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: PartitionedKeyShape2x3<I1, I2, K1, K2, K3>,
    storage: IndexedStoragePlan<R> = auto(),
): LogicalIndexedCacheKey2x3<I1, I2, K1, K2, K3, R> =
    LogicalIndexedCacheKey2x3(name, key.partition, key.entryKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
fun <R, I1, I2, I3, K1, K2, K3> cacheKey(
    name: String,
    returns: LogicalCacheResult<R>,
    key: PartitionedKeyShape3x3<I1, I2, I3, K1, K2, K3>,
    storage: IndexedStoragePlan<R> = auto(),
): LogicalIndexedCacheKey3x3<I1, I2, I3, K1, K2, K3, R> =
    LogicalIndexedCacheKey3x3(name, key.partition, key.entryKey, planIndexed(returns, storage, key.hasMatchableEntryParts))

@ExperimentalKacheableApi
class LogicalExactCacheKey1<P1, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPart<P1>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(p1: P1): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(key.encodePart(p1)),
        partitionPartNames = listOf(key.name),
    )
}

@ExperimentalKacheableApi
class LogicalExactCacheKey2<P1, P2, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPartComposition2<P1, P2>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(p1: P1, p2: P2): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = key.encodeParts(p1, p2),
        partitionPartNames = key.partNames(),
    )
}

@ExperimentalKacheableApi
class LogicalExactCacheKey3<P1, P2, P3, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPartComposition3<P1, P2, P3>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(p1: P1, p2: P2, p3: P3): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = key.encodeParts(p1, p2, p3),
        partitionPartNames = key.partNames(),
    )
}

@ExperimentalKacheableApi
class LogicalExactCacheKey4<P1, P2, P3, P4, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPartComposition4<P1, P2, P3, P4>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = key.encodeParts(p1, p2, p3, p4),
        partitionPartNames = key.partNames(),
    )
}

@ExperimentalKacheableApi
class LogicalExactCacheKey5<P1, P2, P3, P4, P5, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPartComposition5<P1, P2, P3, P4, P5>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = key.encodeParts(p1, p2, p3, p4, p5),
        partitionPartNames = key.partNames(),
    )
}

@ExperimentalKacheableApi
class LogicalExactCacheKey6<P1, P2, P3, P4, P5, P6, R> @PublishedApi internal constructor(
    private val name: String,
    private val key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = key.encodeParts(p1, p2, p3, p4, p5, p6),
        partitionPartNames = key.partNames(),
    )
}

@ExperimentalKacheableApi
class LogicalIndexedCacheKey1x1<I1, K1, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPart<I1>,
    private val entryKey: KeyPart<K1>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(i1: I1, k1: K1): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        entryKeyPartArgs = listOf(entryKey.encodePart(k1)),
        entryKeyPartNames = listOf(entryKey.name),
    )

    fun partition(i1: I1): LogicalCachePartRef<R> = logicalPartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
    )

    fun matching(i1: I1, vararg entryKeyParts: MatchableKeyPartValue): LogicalCachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        entryKeyParts = listOf(entryKey),
        selections = entryKeyParts,
    )
}

@ExperimentalKacheableApi
class LogicalIndexedCacheKey1x2<I1, K1, K2, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPart<I1>,
    private val entryKey: KeyPartComposition2<K1, K2>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(i1: I1, k1: K1, k2: K2): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        entryKeyPartArgs = entryKey.encodeParts(k1, k2),
        entryKeyPartNames = entryKey.partNames(),
    )

    fun partition(i1: I1): LogicalCachePartRef<R> = logicalPartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
    )

    fun matching(i1: I1, vararg entryKeyParts: MatchableKeyPartValue): LogicalCachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = listOf(partition.encodePart(i1)),
        partitionPartNames = listOf(partition.name),
        entryKeyParts = entryKey.parts(),
        selections = entryKeyParts,
    )
}

@ExperimentalKacheableApi
class LogicalIndexedCacheKey2x1<I1, I2, K1, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition2<I1, I2>,
    private val entryKey: KeyPart<K1>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(i1: I1, i2: I2, k1: K1): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
        entryKeyPartArgs = listOf(entryKey.encodePart(k1)),
        entryKeyPartNames = listOf(entryKey.name),
    )

    fun partition(i1: I1, i2: I2): LogicalCachePartRef<R> = logicalPartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
    )
}

@ExperimentalKacheableApi
class LogicalIndexedCacheKey2x3<I1, I2, K1, K2, K3, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition2<I1, I2>,
    private val entryKey: KeyPartComposition3<K1, K2, K3>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(i1: I1, i2: I2, k1: K1, k2: K2, k3: K3): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
        entryKeyPartArgs = entryKey.encodeParts(k1, k2, k3),
        entryKeyPartNames = entryKey.partNames(),
    )

    fun partition(i1: I1, i2: I2): LogicalCachePartRef<R> = logicalPartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
    )

    fun matching(i1: I1, i2: I2, vararg entryKeyParts: MatchableKeyPartValue): LogicalCachePartRef<R> = matchingEntryParts(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2),
        partitionPartNames = partition.partNames(),
        entryKeyParts = entryKey.parts(),
        selections = entryKeyParts,
    )
}

@ExperimentalKacheableApi
class LogicalIndexedCacheKey3x3<I1, I2, I3, K1, K2, K3, R> @PublishedApi internal constructor(
    private val name: String,
    private val partition: KeyPartComposition3<I1, I2, I3>,
    private val entryKey: KeyPartComposition3<K1, K2, K3>,
    private val plannedStorage: PlannedStorage<R>,
) {
    operator fun invoke(i1: I1, i2: I2, i3: I3, k1: K1, k2: K2, k3: K3): LogicalCacheEntryRef<R> = logicalEntryRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partition.encodeParts(i1, i2, i3),
        partitionPartNames = partition.partNames(),
        entryKeyPartArgs = entryKey.encodeParts(k1, k2, k3),
        entryKeyPartNames = entryKey.partNames(),
    )

    fun partition(i1: I1, i2: I2, i3: I3): LogicalCachePartRef<R> = logicalPartRef(
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
    entryKeyParts: List<KeyPart<*>>,
    selections: Array<out MatchableKeyPartValue>,
): LogicalCachePartRef<R> {
    require(selections.isNotEmpty()) { "matching(...) requires at least one matchable entry-key part." }

    val matchableNames = entryKeyParts
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

    val patternPartArgs = entryKeyParts.map { part ->
        selectionsByName[part.name]?.args ?: part.wildcardArgs()
    }

    return logicalPartRef(
        name = name,
        plannedStorage = plannedStorage,
        partitionPartArgs = partitionPartArgs,
        partitionPartNames = partitionPartNames,
        entryKeyPatternPartArgs = patternPartArgs,
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
    entryRef: LogicalCacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R = (this as TypedCacheRuntime).invoke(entryRef.entryRef, entryRef.returnsAs, cacheIf, block)

@ExperimentalKacheableApi
suspend fun <R> Kacheable.cache(
    entryRef: LogicalCacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R = invoke(entryRef, cacheIf, block)

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg entryRefs: LogicalCacheEntryRef<*>) {
    val runtime = this as TypedCacheRuntime
    entryRefs.forEach { runtime.invalidateLogical(it) }
}

@ExperimentalKacheableApi
suspend fun <R> Kacheable.invalidate(
    vararg entryRefs: LogicalCacheEntryRef<*>,
    block: suspend () -> R,
): R {
    val result = block()
    invalidate(*entryRefs)
    return result
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg partRefs: LogicalCachePartRef<*>) {
    val runtime = this as TypedCacheRuntime
    partRefs.forEach { runtime.invalidateLogical(it) }
}

@ExperimentalKacheableApi
suspend fun <R> Kacheable.invalidate(
    vararg partRefs: LogicalCachePartRef<*>,
    block: suspend () -> R,
): R {
    val result = block()
    invalidate(*partRefs)
    return result
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg refs: CacheInvalidationRef) {
    val runtime = this as TypedCacheRuntime
    refs.forEach { runtime.invalidateLogical(it) }
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(refs: Iterable<CacheInvalidationRef>) {
    val runtime = this as TypedCacheRuntime
    refs.forEach { runtime.invalidateLogical(it) }
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
private suspend fun TypedCacheRuntime.invalidateLogical(entryRef: LogicalCacheEntryRef<*>) {
    val returnsAs = entryRef.returnsAs
    if (entryRef.entryRef.storage == CacheStorage.Set && returnsAs is EnumMemberCacheReturn<*>) {
        invalidate(entryRef.entryRef as StoredCacheEntryRef<CacheStorage.Set>, returnsAs as EnumMemberCacheReturn<Any>)
    } else {
        invalidate(entryRef.entryRef)
    }
}

@Suppress("UNCHECKED_CAST")
private suspend fun TypedCacheRuntime.invalidateLogical(partRef: LogicalCachePartRef<*>) {
    val returnsAs = partRef.returnsAs
    if (partRef.partRef.storage == CacheStorage.Set && returnsAs is EnumMemberCacheReturn<*>) {
        invalidate(partRef.partRef as StoredCachePartRef<CacheStorage.Set>, returnsAs as EnumMemberCacheReturn<Any>)
    } else {
        invalidate(partRef.partRef)
    }
}

private suspend fun TypedCacheRuntime.invalidateLogical(ref: CacheInvalidationRef) {
    when (ref) {
        is LogicalCacheEntryRef<*> -> invalidateLogical(ref)
        is LogicalCachePartRef<*> -> invalidateLogical(ref)
    }
}
