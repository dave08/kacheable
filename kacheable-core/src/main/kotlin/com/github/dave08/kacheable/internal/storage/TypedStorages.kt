package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.IsMemberCacheReturn
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.CacheResultPolicy
import com.github.dave08.kacheable.internal.storage.hash.HashMapStorageStrategy
import com.github.dave08.kacheable.internal.storage.set.SetStorageStrategy
import com.github.dave08.kacheable.internal.storage.string.StringStorageStrategy
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.KacheableStore

@OptIn(ExperimentalKacheableApi::class)
internal interface TypedStorage<S : CacheStorage> {
    val storage: S

    suspend fun invalidate(entryRef: StoredCacheEntryRef<S>)

    suspend fun invalidate(partRef: StoredCachePartRef<S>)

    suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R
}

@OptIn(ExperimentalKacheableApi::class)
internal class StringTypedStorage(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    namingStrategy: CacheNamingStrategy,
) : TypedStorage<CacheStorage.String> {
    override val storage: CacheStorage.String = CacheStorage.String
    private val entryNamer = CacheEntryNamer(namingStrategy)

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<CacheStorage.String>) {
        StringStorageStrategy.invalidate(store, entryNamer, entryRef.name, entryRef.cacheArgs, null) {}
    }

    override suspend fun invalidate(partRef: StoredCachePartRef<CacheStorage.String>) {
        StringStorageStrategy.invalidate(
            store,
            entryNamer,
            partRef.name,
            partRef.cacheArgs,
            partRef.secondaryPatternPartArgs,
        ) {}
    }

    override suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.String>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = invokeAtAddress(
        StringStorageStrategy.storeEntryName(entryNamer.nameEntry(entryRef.name, entryRef.cacheArgs)),
        entryRef.name,
        returnsAs.codec,
        saveResultIf,
        block,
    )

    suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        params: Array<out Any>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = invokeAtAddress(
        StringStorageStrategy.storeEntryName(entryNamer.nameEntry(name, params)),
        name,
        codec,
        saveResultIf,
        block,
    )

    suspend fun <R> invalidate(
        vararg keys: Pair<String, List<Any>>,
        block: suspend () -> R,
    ): R {
        keys.forEach { (name, params) ->
            store.delete(StringStorageStrategy.storeEntryName(entryNamer.nameEntry(name, params.toTypedArray())).key)
        }
        return block()
    }

    private suspend fun <R> invokeAtAddress(
        entryName: StoreEntryName,
        cacheName: String,
        codec: CacheValueCodec<R>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        val config = configs[cacheName]
        val result =
            if (entryName is StoreEntryName.Flat && config?.expiryType == ExpiryType.after_access) {
                store.getValueRefreshingExpire(entryName.key, config.expiry)
            } else {
                store.get(entryName)
            }

        return if (result == null) {
            val blockResult = block()
            val resultToSave = CacheResultPolicy.encodeResultToSave(blockResult, config, saveResultIf, codec)
            resultToSave?.let {
                if (entryName is StoreEntryName.Flat && (config?.expiryType ?: ExpiryType.none) != ExpiryType.none) {
                    store.setValueWithExpire(entryName.key, it, config!!.expiry)
                } else {
                    store.mutate {
                        set(entryName, it)
                        if ((config?.expiryType ?: ExpiryType.none) != ExpiryType.none) {
                            setExpire(
                                when (entryName) {
                                    is StoreEntryName.Flat -> entryName.key
                                    is StoreEntryName.Layered -> entryName.key
                                },
                                config!!.expiry,
                            )
                        }
                    }
                }
            }
            blockResult
        } else {
            if (entryName is StoreEntryName.Layered && config?.expiryType == ExpiryType.after_access) {
                store.setExpire(entryName.key, config.expiry)
            }
            CacheResultPolicy.decodeCachedResult(result, config, codec)
        }
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal class HashMapTypedStorage(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    namingStrategy: CacheNamingStrategy,
) : TypedStorage<CacheStorage.HashMap> {
    override val storage: CacheStorage.HashMap = CacheStorage.HashMap
    private val entryNamer = CacheEntryNamer(namingStrategy)

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<CacheStorage.HashMap>) {
        HashMapStorageStrategy.invalidate(store, entryNamer, entryRef.name, entryRef.cacheArgs, null) {}
    }

    override suspend fun invalidate(partRef: StoredCachePartRef<CacheStorage.HashMap>) {
        HashMapStorageStrategy.invalidate(
            store,
            entryNamer,
            partRef.name,
            partRef.cacheArgs,
            partRef.secondaryPatternPartArgs,
        ) {}
    }

    override suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.HashMap>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = invokeAtAddress(
        HashMapStorageStrategy.storeEntryName(entryNamer.nameEntry(entryRef.name, entryRef.cacheArgs)),
        entryRef.name,
        returnsAs.codec,
        saveResultIf,
        block,
    )

    private suspend fun <R> invokeAtAddress(
        entryName: StoreEntryName,
        cacheName: String,
        codec: CacheValueCodec<R>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        val config = configs[cacheName]
        val result =
            if (entryName is StoreEntryName.Flat && config?.expiryType == ExpiryType.after_access) {
                store.getValueRefreshingExpire(entryName.key, config.expiry)
            } else {
                store.get(entryName)
            }

        return if (result == null) {
            val blockResult = block()
            val resultToSave = CacheResultPolicy.encodeResultToSave(blockResult, config, saveResultIf, codec)
            resultToSave?.let {
                if (entryName is StoreEntryName.Flat && (config?.expiryType ?: ExpiryType.none) != ExpiryType.none) {
                    store.setValueWithExpire(entryName.key, it, config!!.expiry)
                } else {
                    store.mutate {
                        set(entryName, it)
                        if ((config?.expiryType ?: ExpiryType.none) != ExpiryType.none) {
                            setExpire(
                                when (entryName) {
                                    is StoreEntryName.Flat -> entryName.key
                                    is StoreEntryName.Layered -> entryName.key
                                },
                                config!!.expiry,
                            )
                        }
                    }
                }
            }
            blockResult
        } else {
            if (entryName is StoreEntryName.Layered && config?.expiryType == ExpiryType.after_access) {
                store.setExpire(entryName.key, config.expiry)
            }
            CacheResultPolicy.decodeCachedResult(result, config, codec)
        }
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal class SetTypedStorage(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val namingStrategy: CacheNamingStrategy,
) : TypedStorage<CacheStorage.Set> {
    override val storage: CacheStorage.Set = CacheStorage.Set

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<CacheStorage.Set>) {
        SetStorageStrategy.invalidateMembership(store, namingStrategy, entryRef.name, entryRef.cacheArgs) {}
    }

    override suspend fun invalidate(partRef: StoredCachePartRef<CacheStorage.Set>) {
        SetStorageStrategy.invalidateMembership(store, namingStrategy, partRef.name, partRef.cacheArgs) {}
    }

    suspend fun <E : Enum<E>> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    ) {
        SetStorageStrategy.invalidateClassification(store, namingStrategy, entryRef.name, entryRef.cacheArgs, returnsAs.valueNames) {}
    }

    suspend fun <E : Enum<E>> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    ) {
        SetStorageStrategy.invalidateClassification(store, namingStrategy, partRef.name, partRef.cacheArgs, returnsAs.valueNames) {}
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = when (returnsAs) {
        is IsMemberCacheReturn -> SetStorageStrategy.invokeMembership(
            store = store,
            configs = configs,
            namingStrategy = namingStrategy,
            name = entryRef.name,
            cacheArgs = entryRef.cacheArgs,
            cacheFalse = returnsAs.cacheFalse,
            saveResultIf = saveResultIf as (Boolean) -> Boolean,
            block = block as suspend () -> Boolean,
        ) as R

        is EnumMemberCacheReturn<*> -> {
            val typedReturn = returnsAs as EnumMemberCacheReturn<out Enum<*>>
            SetStorageStrategy.invokeClassification(
                store = store,
                configs = configs,
                namingStrategy = namingStrategy,
                name = entryRef.name,
                cacheArgs = entryRef.cacheArgs,
                values = typedReturn.values,
                valueName = typedReturn.valueName as (Enum<*>) -> String,
                saveResultIf = saveResultIf as (Enum<*>) -> Boolean,
                block = block as suspend () -> Enum<*>,
            ) as R
        }

        else -> error("Set storage does not support return view ${returnsAs::class.simpleName}.")
    }
}

@OptIn(ExperimentalKacheableApi::class)
internal data class TypedStorages(
    val string: StringTypedStorage,
    val hashMap: HashMapTypedStorage,
    val set: SetTypedStorage,
) {
    fun any(storage: CacheStorage): TypedStorage<out CacheStorage> = when (storage) {
        CacheStorage.String -> string
        CacheStorage.HashMap -> hashMap
        CacheStorage.Set -> set
    }
}
