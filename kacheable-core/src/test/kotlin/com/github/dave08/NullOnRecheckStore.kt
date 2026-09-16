package com.github.dave08

import com.github.dave08.kacheable.store.InMemoryKacheableStore
import com.github.dave08.kacheable.store.KacheableStore

/** Models another writer publishing null between the initial read and coordination recheck. */
internal class NullOnRecheckStore : KacheableStore by InMemoryKacheableStore() {
    private var reads = 0

    override suspend fun get(key: String): String = if (reads++ == 0) "\"old\"" else "<null>"
}
