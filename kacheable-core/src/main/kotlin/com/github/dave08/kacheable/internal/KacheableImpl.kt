package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheEntryName
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.cacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

@OptIn(ExperimentalKacheableApi::class)
internal class KacheableImpl(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val namingStrategy: CacheNamingStrategy,
    private val jsonParser: Json,
) : Kacheable {
    private val entryNameResolver = CacheEntryNameResolver(namingStrategy)

    override suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R {
        keys.forEach { (name, params) ->
            store.delete(
                namingStrategy.getEntryName(name, CacheStorage.String, params.toTypedArray(), emptyArray()).cacheKey,
            )
        }

        return block()
    }

    @ExperimentalKacheableApi
    override suspend fun <R> invalidate(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        storage: CacheStorage,
        secondaryPatternPartArgs: List<CacheArgs>?,
        block: suspend () -> R,
    ): R {
        if (secondaryPatternPartArgs != null) {
            store.deleteMatching(
                entryNameResolver.resolvePattern(name, cacheArgs.primary, secondaryPatternPartArgs, storage) as
                    CacheEntryName.Layered,
            )
        } else {
            store.mutate {
                delete(entryNameResolver.resolve(name, cacheArgs, storage))
            }
        }
        return block()
    }

    override suspend fun <R> invalidateSetMembership(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        block: suspend () -> R,
    ): R {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val plan = membershipEntry.invalidationPlan()
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
        cacheArgs: PrimarySecondaryCacheArgs,
        valueNames: List<String>,
        block: suspend () -> R,
    ): R {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val plan = membershipEntry.classificationInvalidationPlan(valueNames)
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
            entryName = entryNameResolver.resolve(name, params),
            cacheName = name,
            codec = codec,
            saveResultIf = saveResultIf,
            block = block,
        )
    }

    override suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        cacheArgs: PrimarySecondaryCacheArgs,
        storage: CacheStorage,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        return invokeAtAddress(
            entryName = entryNameResolver.resolve(name, cacheArgs, storage),
            cacheName = name,
            codec = codec,
            saveResultIf = saveResultIf,
            block = block,
        )
    }

    override suspend fun invokeSetMembership(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        cacheFalse: Boolean,
        saveResultIf: (Boolean) -> Boolean,
        block: suspend () -> Boolean,
    ): Boolean {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        if (store.isSetMember(membershipEntry.membersKey, member)) {
            if (config?.expiryType == ExpiryType.after_access)
                store.setExpire(membershipEntry.membersKey, config.expiry)
            return true
        }

        if (cacheFalse && store.isSetMember(membershipEntry.nonMembersKey, member)) {
            if (config?.expiryType == ExpiryType.after_access)
                store.setExpire(membershipEntry.nonMembersKey, config.expiry)
            return false
        }

        val blockResult = block()
        if (shouldWriteSetMembershipResult(blockResult, cacheFalse, saveResultIf)) {
            store.replaceSetMembership(
                member = member,
                membersKey = membershipEntry.membersKey,
                nonMembersKey = membershipEntry.nonMembersKey,
                isMember = blockResult,
                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                cacheFalse = cacheFalse,
            )
        }

        return blockResult
    }

    override suspend fun <R : Any> invokeSetClassification(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        values: List<R>,
        valueName: (R) -> String,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        require(values.isNotEmpty()) { "Set classification caches require at least one possible value." }
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        values.forEach { value ->
            val key = membershipEntry.classifiedKey(valueName(value))
            if (store.isSetMember(key, member)) {
                if (config?.expiryType == ExpiryType.after_access)
                    store.setExpire(key, config.expiry)
                return value
            }
        }

        val blockResult = block()
        if (saveResultIf(blockResult)) {
            val keyToWrite = membershipEntry.keyForClassificationResult(blockResult, values, valueName)
            store.replaceClassifiedMembership(
                member = member,
                targetKey = keyToWrite,
                candidateKeys = values.map { value -> membershipEntry.classifiedKey(valueName(value)) },
                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
            )
        }

        return blockResult
    }

    private suspend fun <R> invokeAtAddress(
        entryName: CacheEntryName,
        cacheName: String,
        codec: CacheValueCodec<R>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        val config = configs[cacheName]
        val result =
            if (entryName is CacheEntryName.Flat && config?.expiryType == ExpiryType.after_access) {
                store.getValueRefreshingExpire(entryName.cacheKey, config.expiry)
            } else {
                store.get(entryName)
            }

        return if (result == null) {
            val blockResult = block()

            val resultToSave = CacheResultPolicy.encodeResultToSave(blockResult, config, saveResultIf, codec)

            resultToSave?.let {
                if (entryName is CacheEntryName.Flat && (config?.expiryType ?: ExpiryType.none) != ExpiryType.none) {
                    store.setValueWithExpire(entryName.cacheKey, it, config!!.expiry)
                } else {
                    store.mutate {
                        set(entryName, it)

                        if ((config?.expiryType ?: ExpiryType.none) != ExpiryType.none)
                            setExpire(entryName.cacheKey, config!!.expiry)
                    }
                }
            }

            blockResult
        } else {
            // Set expiry after access
            if (entryName is CacheEntryName.Layered && config?.expiryType == ExpiryType.after_access)
                store.setExpire(entryName.cacheKey, config.expiry)

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
