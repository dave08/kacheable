package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheKeyGroups
import com.github.dave08.kacheable.CacheStorageLayout
import com.github.dave08.kacheable.CacheWildcard
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.GetNameStrategy
import com.github.dave08.kacheable.KacheableStore

@ExperimentalKacheableApi
internal data class CacheStorageAddress(
    val key: String,
    val field: String? = null,
)

@OptIn(ExperimentalKacheableApi::class)
internal class CacheStorageAddressResolver(
    private val getNameStrategy: GetNameStrategy,
) {
    fun resolve(name: String, params: Array<out Any>): CacheStorageAddress =
        CacheStorageAddress(getNameStrategy.getName(name, params))

    fun resolve(
        name: String,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
    ): CacheStorageAddress {
        return when (storageLayout) {
            CacheStorageLayout.StringValue ->
                CacheStorageAddress(getNameStrategy.getName(name, keyGroups.flattened.toParamsArray()))
            CacheStorageLayout.HashValue -> resolveHashValue(name, keyGroups)
        }
    }

    private fun resolveHashValue(
        name: String,
        keyGroups: CacheKeyGroups,
    ): CacheStorageAddress {
        val secondary = keyGroups.secondary ?: return CacheStorageAddress(
            getNameStrategy.getName(name, keyGroups.flattened.toParamsArray()),
        )

        if (secondary.isWildcard())
            return CacheStorageAddress(getNameStrategy.getName(name, keyGroups.main.toParamsArray()))

        return CacheStorageAddress(
            key = getNameStrategy.getName(name, keyGroups.main.toParamsArray()),
            field = secondary.toParamsArray().joinToString(","),
        )
    }

    private fun CacheArgs.isWildcard(): Boolean =
        toParamsArray().all { it == CacheWildcard }
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.get(address: CacheStorageAddress): String? =
    if (address.field == null)
        get(address.key)
    else
        getHashValue(address.key, address.field)

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.set(address: CacheStorageAddress, value: String) {
    address.field?.let { setHashValue(address.key, it, value) } ?: set(address.key, value)
}

@OptIn(ExperimentalKacheableApi::class)
internal suspend fun KacheableStore.delete(address: CacheStorageAddress) {
    if (address.field == null)
        delete(address.key)
    else
        deleteHashValue(address.key, address.field)
}
