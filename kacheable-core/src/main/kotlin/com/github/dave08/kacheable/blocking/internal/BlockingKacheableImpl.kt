package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheKeyGroups
import com.github.dave08.kacheable.CacheStorageLayout
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.internal.CacheResultPolicy
import com.github.dave08.kacheable.internal.CacheStorageAddress
import com.github.dave08.kacheable.internal.CacheStorageAddressResolver
import com.github.dave08.kacheable.internal.classificationInvalidationPlan
import com.github.dave08.kacheable.internal.invalidationPlan
import com.github.dave08.kacheable.internal.keyForClassificationResult
import com.github.dave08.kacheable.internal.setMembershipAddress
import com.github.dave08.kacheable.internal.shouldWriteSetMembershipResult
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.cacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

@OptIn(ExperimentalKacheableApi::class)
internal class BlockingKacheableImpl(
    private val store: BlockingKacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val getNameStrategy: GetNameStrategy,
    private val jsonParser: Json
) : BlockingKacheable {
    private val addressResolver = CacheStorageAddressResolver(getNameStrategy)

    override fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R {
        keys.forEach { (name, params) ->
            store.delete(getNameStrategy.getName(name, params.toTypedArray()))
        }

        return block()
    }

    override fun <R> invalidate(
        name: String,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
        block: () -> R,
    ): R {
        store.mutate {
            delete(addressResolver.resolve(name, keyGroups, storageLayout))
        }
        return block()
    }

    override fun <R> invalidateSetMembership(
        name: String,
        keyGroups: CacheKeyGroups,
        block: () -> R,
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

    override fun <R> invalidateSetClassification(
        name: String,
        keyGroups: CacheKeyGroups,
        valueNames: List<String>,
        block: () -> R,
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

    override fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R {
        return invokeAtAddress(
            address = addressResolver.resolve(name, params),
            cacheName = name,
            codec = codec,
            saveResultIf = saveResultIf,
            block = block,
        )
    }

    override fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R {
        return invokeAtAddress(
            address = addressResolver.resolve(name, keyGroups, storageLayout),
            cacheName = name,
            codec = codec,
            saveResultIf = saveResultIf,
            block = block,
        )
    }

    override fun invokeSetMembership(
        name: String,
        keyGroups: CacheKeyGroups,
        cacheFalse: Boolean,
        saveResultIf: (Boolean) -> Boolean,
        block: () -> Boolean,
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
            val keyToWrite = address.keyFor(blockResult)
            store.mutate {
                addSetMember(keyToWrite, member)
                if ((config?.expiryType ?: ExpiryType.none) != ExpiryType.none)
                    setExpire(keyToWrite, config!!.expiry)
            }
        }

        return blockResult
    }

    override fun <R : Any> invokeSetClassification(
        name: String,
        keyGroups: CacheKeyGroups,
        values: List<R>,
        valueName: (R) -> String,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
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
            store.mutate {
                values.forEach { value ->
                    deleteSetMember(address.classifiedKey(valueName(value)), member)
                }
                addSetMember(keyToWrite, member)
                if ((config?.expiryType ?: ExpiryType.none) != ExpiryType.none)
                    setExpire(keyToWrite, config!!.expiry)
            }
        }

        return blockResult
    }

    private fun <R> invokeAtAddress(
        address: CacheStorageAddress,
        cacheName: String,
        codec: CacheValueCodec<R>,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R {
        val result = store.get(address)
        val config = configs[cacheName]

        return if (result == null) {
            val blockResult = block()

            val resultToSave = CacheResultPolicy.encodeResultToSave(blockResult, config, saveResultIf, codec)

            resultToSave?.let {
                store.mutate {
                    set(address, it)

                    if ((config?.expiryType ?: ExpiryType.none) != ExpiryType.none)
                        setExpire(address.key, config!!.expiry)
                }
            }

            blockResult
        } else {
            // Set expiry after access
            if (config?.expiryType == ExpiryType.after_access)
                store.setExpire(address.key, config.expiry)

            CacheResultPolicy.decodeCachedResult(result, config, codec)
        }
    }

    override fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R = invoke(
        name = name,
        codec = cacheValueCodec(type, jsonParser),
        params = params,
        saveResultIf = saveResultIf,
        block = block,
    )
}
