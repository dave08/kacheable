package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.internal.CacheResultPolicy
import com.github.dave08.kacheable.internal.OperationObservation
import com.github.dave08.kacheable.internal.storage.hash.HashMapStorageStrategy
import com.github.dave08.kacheable.internal.storage.string.StringStorageStrategy
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.StoreMutationScope

/** Storage representation only: no loader, fallback, refresh, or coordination decisions. */
internal class SelectedEntryStorage<K, V>(
    private val entry: (K) -> CacheEntryRef<V>,
    private val store: KacheableStore,
    private val naming: CacheNamingStrategy,
    private val config: CacheConfig?,
    private val observation: OperationObservation,
) {
    private val namer = CacheEntryNamer(naming)
    private fun ref(key: K) = entry(key).entryRef
    fun address(key: K): StoreEntryName {
        val ref = ref(key)
        val name = namer.nameEntry(ref.name, ref.cacheArgs)
        return when (ref.storage) {
            CacheStorage.String -> StringStorageStrategy.storeEntryName(name)
            CacheStorage.HashMap -> HashMapStorageStrategy.storeEntryName(name)
            CacheStorage.Set -> membership(key).let { StoreEntryName.Layered(it.membersKey, it.requiredMember) }
        }
    }
    fun identity(key: K): String = when (val address = address(key)) {
        is StoreEntryName.Flat -> address.key
        is StoreEntryName.Layered -> if (ref(key).storage == CacheStorage.Set)
            "${address.key}:${address.entry}" else "${address.key}::${address.entry}"
    }
    private fun membership(key: K) = ref(key).let { setMembershipEntry(it.name, it.cacheArgs, naming) }

    suspend fun read(keys: List<K>, attempt: CacheReadAttempt): Map<K, CachedValue<V>> {
        if (keys.isEmpty()) return emptyMap()
        val started = observation.startTimer()
        try {
            val result = linkedMapOf<K, CachedValue<V>>()
            val first = entry(keys.first())
            when (val view = first.returnView) {
                is IsMemberCacheReturn -> {
                    for ((_, group) in keys.groupBy { membership(it).membersKey }) {
                        val spec = membership(group.first())
                        val members = group.map { membership(it).requiredMember }.distinct()
                        val positive = store.areSetMembers(spec.membersKey, members)
                        val negative = if (view.cacheFalse) store.areSetMembers(spec.nonMembersKey, members.filterNot(positive::contains)) else emptySet()
                        for (key in group) {
                            val member = membership(key).requiredMember
                            val value = when (member) { in positive -> true; in negative -> false; else -> continue }
                            @Suppress("UNCHECKED_CAST")
                            result[key] = CachedValue(value as V)
                        }
                        touch(spec.membersKey, positive.isNotEmpty())
                        touch(spec.nonMembersKey, negative.isNotEmpty())
                    }
                }
                is EnumMemberCacheReturn<*> -> {
                    for ((_, group) in keys.groupBy { membership(it).membersKey }) {
                        val spec = membership(group.first())
                        for ((index, value) in view.values.withIndex()) {
                            val unresolved = group.filterNot(result::containsKey)
                            if (unresolved.isEmpty()) break
                            val classifiedKey = spec.classifiedKey(view.valueNames[index])
                            val present = store.areSetMembers(classifiedKey, unresolved.map { membership(it).requiredMember }.distinct())
                            for (key in unresolved) if (membership(key).requiredMember in present) {
                                @Suppress("UNCHECKED_CAST")
                                result[key] = CachedValue(value as V)
                            }
                            touch(classifiedKey, present.isNotEmpty())
                        }
                    }
                }
                else -> {
                    val flat = keys.filter { address(it) is StoreEntryName.Flat }
                    if (flat.isNotEmpty()) {
                        val names = flat.map { (address(it) as StoreEntryName.Flat).key }.distinct()
                        val raw = if (config?.expiryType == ExpiryType.after_access)
                            store.getValuesRefreshingExpire(names, config.expiry) else store.getValues(names)
                        for (key in flat) raw[(address(key) as StoreEntryName.Flat).key]?.let {
                            result[key] = CachedValue(CacheResultPolicy.decodeCachedResult(it, config, entry(key).returnView.codec))
                        }
                    }
                    val layered = keys.filter { address(it) is StoreEntryName.Layered }
                    for ((hash, group) in layered.groupBy { (address(it) as StoreEntryName.Layered).key }) {
                        val fields = group.map { (address(it) as StoreEntryName.Layered).entry }.distinct()
                        val raw = store.getHashValues(hash, fields)
                        for (key in group) raw[(address(key) as StoreEntryName.Layered).entry]?.let {
                            result[key] = CachedValue(CacheResultPolicy.decodeCachedResult(it, config, entry(key).returnView.codec))
                        }
                        touch(hash, raw.isNotEmpty())
                    }
                }
            }
            observation.storageRead(attempt, if (result.isEmpty()) CacheReadResult.Absent else CacheReadResult.Present, started)
            return result
        } catch (failure: Throwable) {
            observation.storageRead(attempt, CacheReadResult.Failed, started)
            throw failure
        }
    }

    private suspend fun touch(key: String, present: Boolean) {
        if (present && config?.expiryType == ExpiryType.after_access) store.setExpire(key, config.expiry)
    }

    /** Plans every value before submitting the existing grouped mutation operation. */
    suspend fun publish(values: Map<K, V>, predicate: (V) -> Boolean) {
        val writes = mutableListOf<suspend StoreMutationScope.() -> Unit>()
        val touched = linkedSetOf<String>()
        for ((key, value) in values) {
            val view = entry(key).returnView
            val write: (suspend StoreMutationScope.() -> Unit)? = when (view) {
                is IsMemberCacheReturn -> {
                    val boolean = value as Boolean
                    if (!shouldWriteSetMembershipResult(boolean, view.cacheFalse) { predicate(value) }) null
                    else {
                        val spec = membership(key)
                        touched += spec.keyFor(boolean)
                        mutation {
                            deleteSetMember(spec.membersKey, spec.requiredMember)
                            if (view.cacheFalse) deleteSetMember(spec.nonMembersKey, spec.requiredMember)
                            addSetMember(spec.keyFor(boolean), spec.requiredMember)
                        }
                    }
                }
                is EnumMemberCacheReturn<*> -> {
                    if (!predicate(value)) null else {
                        val spec = membership(key)
                        val index = view.values.indexOfFirst { it == value }
                        require(index >= 0) { "Classification value is not a configured enum member." }
                        val target = spec.classifiedKey(view.valueNames[index])
                        touched += target
                        mutation {
                            view.valueNames.forEach { deleteSetMember(spec.classifiedKey(it), spec.requiredMember) }
                            addSetMember(target, spec.requiredMember)
                        }
                    }
                }
                else -> {
                    val raw = CacheResultPolicy.encodeResultToSave(value, config, predicate, view.codec)
                    if (raw == null) null else {
                        val address = address(key)
                        touched += when (address) { is StoreEntryName.Flat -> address.key; is StoreEntryName.Layered -> address.key }
                        mutation { set(address, raw) }
                    }
                }
            }
            if (write != null) writes += write
            else observation.storageWrite(CacheWriteResult.Skipped, observation.startTimer())
        }
        if (writes.isEmpty()) return
        val started = observation.startTimer()
        try {
            store.mutate {
                writes.forEach { it() }
                if (config != null && config.expiryType != ExpiryType.none) touched.forEach { setExpire(it, config.expiry) }
            }
            observation.storageWrite(CacheWriteResult.Stored, started)
        } catch (failure: Throwable) {
            observation.storageWrite(CacheWriteResult.Failed, started)
            throw failure
        }
    }
}

private fun mutation(block: suspend StoreMutationScope.() -> Unit): suspend StoreMutationScope.() -> Unit = block
