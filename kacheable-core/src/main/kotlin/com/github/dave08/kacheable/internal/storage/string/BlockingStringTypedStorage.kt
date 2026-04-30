package com.github.dave08.kacheable.internal.storage.string

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.internal.CacheResultPolicy
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorage
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.storage.StoreEntryName
import com.github.dave08.kacheable.internal.storage.delete
import com.github.dave08.kacheable.internal.storage.get
import com.github.dave08.kacheable.internal.storage.set
import com.github.dave08.kacheable.store.CacheValueCodec

@OptIn(ExperimentalKacheableApi::class)
internal class BlockingStringTypedStorage(
    private val store: BlockingKacheableStore,
    private val configs: Map<String, CacheConfig>,
    namingStrategy: CacheNamingStrategy,
) : BlockingTypedStorage<CacheStorage.String> {
    override val storage: CacheStorage.String = CacheStorage.String
    private val entryNamer = CacheEntryNamer(namingStrategy)

    override fun invalidate(entryRef: StoredCacheEntryRef<CacheStorage.String>) {
        StringStorageStrategy.invalidate(store, entryNamer, entryRef.name, entryRef.cacheArgs, null) {}
    }

    override fun invalidate(partRef: StoredCachePartRef<CacheStorage.String>) {
        StringStorageStrategy.invalidate(store, entryNamer, partRef.name, partRef.cacheArgs, partRef.secondaryPatternPartArgs) {}
    }

    override fun <R> invoke(
        entryRef: StoredCacheEntryRef<CacheStorage.String>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R = invokeAtAddress(
        StringStorageStrategy.storeEntryName(entryNamer.nameEntry(entryRef.name, entryRef.cacheArgs)),
        entryRef.name,
        returnsAs.codec,
        saveResultIf,
        block,
    )

    fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        params: Array<out Any>,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R = invokeAtAddress(
        StringStorageStrategy.storeEntryName(entryNamer.nameEntry(name, params)),
        name,
        codec,
        saveResultIf,
        block,
    )

    fun <R> invalidate(
        vararg keys: Pair<String, List<Any>>,
        block: () -> R,
    ): R {
        keys.forEach { (name, params) ->
            store.delete(StringStorageStrategy.storeEntryName(entryNamer.nameEntry(name, params.toTypedArray())).key)
        }
        return block()
    }

    private fun <R> invokeAtAddress(
        entryName: StoreEntryName,
        cacheName: String,
        codec: CacheValueCodec<R>,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
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
