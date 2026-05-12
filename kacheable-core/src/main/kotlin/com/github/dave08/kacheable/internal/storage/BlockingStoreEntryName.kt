package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingStoreMutationScope

internal fun BlockingKacheableStore.get(entryName: StoreEntryName): String? =
    when (entryName) {
        is StoreEntryName.Flat -> get(entryName.key)
        is StoreEntryName.Layered -> getHashValue(entryName.key, entryName.entry)
    }

internal fun BlockingKacheableStore.set(entryName: StoreEntryName, value: String) {
    when (entryName) {
        is StoreEntryName.Flat -> set(entryName.key, value)
        is StoreEntryName.Layered -> setHashValue(entryName.key, entryName.entry, value)
    }
}

internal fun BlockingStoreMutationScope.set(entryName: StoreEntryName, value: String) {
    when (entryName) {
        is StoreEntryName.Flat -> set(entryName.key, value)
        is StoreEntryName.Layered -> setHashValue(entryName.key, entryName.entry, value)
    }
}

internal fun BlockingKacheableStore.delete(entryName: StoreEntryName) {
    when (entryName) {
        is StoreEntryName.Flat -> delete(entryName.key)
        is StoreEntryName.Layered -> deleteHashValue(entryName.key, entryName.entry)
    }
}

internal fun BlockingKacheableStore.deleteMatching(entryName: StoreEntryName.Layered) {
    deleteHashValuesMatching(entryName.key, entryName.entry)
}

internal fun BlockingStoreMutationScope.delete(entryName: StoreEntryName) {
    when (entryName) {
        is StoreEntryName.Flat -> delete(entryName.key)
        is StoreEntryName.Layered -> deleteHashValue(entryName.key, entryName.entry)
    }
}
