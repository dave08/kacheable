package com.github.dave08

import com.github.dave08.kacheable.blocking.store.BlockingVersionedHashOperations
import com.github.dave08.kacheable.store.HashPublishResult
import com.github.dave08.kacheable.store.HashReadSnapshot
import com.github.dave08.kacheable.store.HashVersion
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import com.github.dave08.kacheable.store.VersionedHashOperations
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val HashReadSnapshotSpec by testSuite {
    test("default suspending snapshot reports a conflict between opening and reading") {
        val expectedVersion = HashVersion("generation", 7)
        val store = ConflictingSnapshotStore(expectedVersion)

        assertNull(store.readHashSnapshot("delivery", 10.seconds, listOf("page")))
        assertEquals(listOf("open:delivery:10s", "read:delivery:$expectedVersion:[page]"), store.calls)
    }

    test("default blocking snapshot reports a conflict between opening and reading") {
        val expectedVersion = HashVersion("generation", 7)
        val store = ConflictingBlockingSnapshotStore(expectedVersion)

        assertNull(store.readHashSnapshot("delivery", 10.seconds, listOf("page")))
        assertEquals(listOf("open:delivery:10s", "read:delivery:$expectedVersion:[page]"), store.calls)
    }

    test("in-memory snapshot returns the opened version and selected values without renewing expiry") {
        var now = 0L
        val store = InMemoryKacheableStore(nanoTime = { now })
        val initial = store.openHash("delivery", 10.seconds)
        val current = (store.publishHash("delivery", initial, "first", "one", 10.seconds, true) as HashPublishResult.Published).version
        store.publishHash("delivery", current, "second", "two", 10.seconds, true)
        now = 5.seconds.inWholeNanoseconds

        val snapshot = store.readHashSnapshot("delivery", 10.seconds, listOf("second", "missing"))

        assertEquals(HashReadSnapshot(HashVersion(current.generation, current.revision + 1), mapOf("second" to "two")), snapshot)
        now = 11.seconds.inWholeNanoseconds
        assertNotEquals(snapshot?.version?.generation, store.openHash("delivery", 10.seconds).generation)
    }
}

private class ConflictingSnapshotStore(
    private val openedVersion: HashVersion,
) : VersionedHashOperations {
    val calls = mutableListOf<String>()

    override suspend fun deleteHashes(keyPattern: String) = Unit

    override suspend fun openHash(key: String, expiry: Duration?): HashVersion {
        calls += "open:$key:$expiry"
        return openedVersion
    }

    override suspend fun readHash(key: String, version: HashVersion, fields: List<String>?): Map<String, String>? {
        calls += "read:$key:$version:$fields"
        return null
    }

    override suspend fun readHashMetadata(key: String, version: HashVersion): Map<String, String?>? = null

    override suspend fun publishHash(
        key: String,
        version: HashVersion,
        field: String,
        value: String,
        expiry: Duration?,
        ifAbsent: Boolean,
        metadata: String?,
    ): HashPublishResult = HashPublishResult.Conflict
}

private class ConflictingBlockingSnapshotStore(
    private val openedVersion: HashVersion,
) : BlockingVersionedHashOperations {
    val calls = mutableListOf<String>()

    override fun deleteHashes(keyPattern: String) = Unit

    override fun openHash(key: String, expiry: Duration?): HashVersion {
        calls += "open:$key:$expiry"
        return openedVersion
    }

    override fun readHash(key: String, version: HashVersion, fields: List<String>?): Map<String, String>? {
        calls += "read:$key:$version:$fields"
        return null
    }

    override fun readHashMetadata(key: String, version: HashVersion): Map<String, String?>? = null

    override fun publishHash(
        key: String,
        version: HashVersion,
        field: String,
        value: String,
        expiry: Duration?,
        ifAbsent: Boolean,
        metadata: String?,
    ): HashPublishResult = HashPublishResult.Conflict
}
