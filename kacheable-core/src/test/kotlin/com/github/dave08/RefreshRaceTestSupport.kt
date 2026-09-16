package com.github.dave08

import com.github.dave08.kacheable.store.InMemoryKacheableStore
import com.github.dave08.kacheable.store.KacheableStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

internal class RefreshRaceStore(
    private val backingStore: InMemoryKacheableStore = InMemoryKacheableStore(),
) : KacheableStore by backingStore {
    private val initialTargetRead = CompletableDeferred<Unit>()
    private val targetReads = AtomicInteger()

    override suspend fun getHashValue(key: String, field: String): String? {
        val value = backingStore.getHashValue(key, field)
        if (key == TARGET_HASH_KEY && field == TARGET_FIELD && targetReads.incrementAndGet() == 1) {
            initialTargetRead.complete(Unit)
        }
        return value
    }

    fun arrangeOldTargetValue() {
        storeTargetValue(OLD_TARGET_JSON)
    }

    suspend fun awaitInitialTargetRead() = initialTargetRead.await()

    fun replaceTargetWithNewerValue() {
        storeTargetValue(NEWER_TARGET_JSON)
    }

    private fun storeTargetValue(value: String) {
        backingStore.hashMap.getOrPut(TARGET_HASH_KEY, ::mutableMapOf)[TARGET_FIELD] = value
    }

    private companion object {
        const val TARGET_HASH_KEY = "miss-policy-cache:11"
        const val TARGET_FIELD = "target"
        const val OLD_TARGET_JSON = """{"id":11,"value":"old"}"""
        const val NEWER_TARGET_JSON = """{"id":11,"value":"newer"}"""
    }
}
