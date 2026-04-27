package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.blocking.BlockingKacheableStore
import com.github.dave08.kacheable.internal.CacheStorageAddress

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingKacheableStore.get(address: CacheStorageAddress): String? =
    if (address.field == null)
        get(address.key)
    else
        getHashValue(address.key, address.field)

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingKacheableStore.set(address: CacheStorageAddress, value: String) {
    address.field?.let { setHashValue(address.key, it, value) } ?: set(address.key, value)
}

@OptIn(ExperimentalKacheableApi::class)
internal fun BlockingKacheableStore.delete(address: CacheStorageAddress) {
    if (address.field == null)
        delete(address.key)
    else
        deleteHashValue(address.key, address.field)
}
