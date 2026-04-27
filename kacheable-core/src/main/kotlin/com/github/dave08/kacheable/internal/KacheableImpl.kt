package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheKeyGroups
import com.github.dave08.kacheable.CacheStorageLayout
import com.github.dave08.kacheable.DefaultGetNameStrategy
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.cacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

@OptIn(ExperimentalKacheableApi::class)
internal class KacheableImpl(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val getNameStrategy: GetNameStrategy,
    private val jsonParser: Json,
) : Kacheable {
    private val addressResolver = CacheStorageAddressResolver(getNameStrategy)

    override suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R {
        keys.forEach { (name, params) ->
            store.delete(getNameStrategy.getName(name, params.toTypedArray()))
        }

        return block()
    }

    @ExperimentalKacheableApi
    override suspend fun <R> invalidate(
        name: String,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
        block: suspend () -> R,
    ): R {
        store.mutate {
            delete(addressResolver.resolve(name, keyGroups, storageLayout))
        }
        return block()
    }

    override suspend fun <R> invalidateSetMembership(
        name: String,
        keyGroups: CacheKeyGroups,
        block: suspend () -> R,
    ): R {
        val address = setMembershipAddress(name, keyGroups, getNameStrategy)
        val plan = address.invalidationPlan()
        store.mutate {
            plan.keys.forEach { delete(it) }
            plan.members.forEach { (key, member) ->
                deleteSetMember(key, member)
            }
        }
        return block()
    }

    override suspend fun <R> invalidateSetClassification(
        name: String,
        keyGroups: CacheKeyGroups,
        valueNames: List<String>,
        block: suspend () -> R,
    ): R {
        val address = setMembershipAddress(name, keyGroups, getNameStrategy)
        val plan = address.classificationInvalidationPlan(valueNames)
        store.mutate {
            plan.keys.forEach { delete(it) }
            plan.members.forEach { (key, member) ->
                deleteSetMember(key, member)
            }
        }
        return block()
    }

    override suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R
    ): R {
        return invokeAtAddress(
            address = addressResolver.resolve(name, params),
            cacheName = name,
            codec = codec,
            saveResultIf = saveResultIf,
            block = block,
        )
    }

    override suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        return invokeAtAddress(
            address = addressResolver.resolve(name, keyGroups, storageLayout),
            cacheName = name,
            codec = codec,
            saveResultIf = saveResultIf,
            block = block,
        )
    }

    override suspend fun invokeSetMembership(
        name: String,
        keyGroups: CacheKeyGroups,
        cacheFalse: Boolean,
        saveResultIf: (Boolean) -> Boolean,
        block: suspend () -> Boolean,
    ): Boolean {
        val address = setMembershipAddress(name, keyGroups, getNameStrategy)
        val member = address.requiredMember
        val config = configs[name]

        if (store.isSetMember(address.membersKey, member)) {
            if (config?.expiryType == ExpiryType.after_access)
                store.setExpire(address.membersKey, config.expiry)
            return true
        }

        if (cacheFalse && store.isSetMember(address.nonMembersKey, member)) {
            if (config?.expiryType == ExpiryType.after_access)
                store.setExpire(address.nonMembersKey, config.expiry)
            return false
        }

        val blockResult = block()
        if (shouldWriteSetMembershipResult(blockResult, cacheFalse, saveResultIf)) {
            store.replaceSetMembership(
                member = member,
                membersKey = address.membersKey,
                nonMembersKey = address.nonMembersKey,
                isMember = blockResult,
                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                cacheFalse = cacheFalse,
            )
        }

        return blockResult
    }

    override suspend fun <R : Any> invokeSetClassification(
        name: String,
        keyGroups: CacheKeyGroups,
        values: List<R>,
        valueName: (R) -> String,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        require(values.isNotEmpty()) { "Set classification caches require at least one possible value." }
        val address = setMembershipAddress(name, keyGroups, getNameStrategy)
        val member = address.requiredMember
        val config = configs[name]

        values.forEach { value ->
            val key = address.classifiedKey(valueName(value))
            if (store.isSetMember(key, member)) {
                if (config?.expiryType == ExpiryType.after_access)
                    store.setExpire(key, config.expiry)
                return value
            }
        }

        val blockResult = block()
        if (saveResultIf(blockResult)) {
            val keyToWrite = address.keyForClassificationResult(blockResult, values, valueName)
            store.replaceClassifiedMembership(
                member = member,
                targetKey = keyToWrite,
                candidateKeys = values.map { value -> address.classifiedKey(valueName(value)) },
                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
            )
        }

        return blockResult
    }

    private suspend fun <R> invokeAtAddress(
        address: CacheStorageAddress,
        cacheName: String,
        codec: CacheValueCodec<R>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        val config = configs[cacheName]
        val result =
            if (address.field == null && config?.expiryType == ExpiryType.after_access) {
                store.getValueRefreshingExpire(address.key, config.expiry)
            } else {
                store.get(address)
            }

        return if (result == null) {
            val blockResult = block()

            val resultToSave = CacheResultPolicy.encodeResultToSave(blockResult, config, saveResultIf, codec)

            resultToSave?.let {
                if (address.field == null && (config?.expiryType ?: ExpiryType.none) != ExpiryType.none) {
                    store.setValueWithExpire(address.key, it, config!!.expiry)
                } else {
                    store.mutate {
                        set(address, it)

                        if ((config?.expiryType ?: ExpiryType.none) != ExpiryType.none)
                            setExpire(address.key, config!!.expiry)
                    }
                }
            }

            blockResult
        } else {
            // Set expiry after access
            if (address.field != null && config?.expiryType == ExpiryType.after_access)
                store.setExpire(address.key, config.expiry)

            CacheResultPolicy.decodeCachedResult(result, config, codec)
        }
    }

    override suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = invoke(
        name = name,
        codec = cacheValueCodec(type, jsonParser),
        params = params,
        saveResultIf = saveResultIf,
        block = block,
    )
}
