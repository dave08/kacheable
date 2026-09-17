package com.github.dave08

import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.store.KacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

val StoreBatchReadSpec by testSuite {
    test("suspending store defaults preserve keys while omitting string cache misses") {
        val store = RecordingSuspendStore(values = mapOf("song:1" to "first", "song:2" to "second"))

        val result = store.getValues(listOf("song:2", "song:missing", "song:1"))

        assertEquals(mapOf("song:2" to "second", "song:1" to "first"), result)
        assertEquals(listOf("song:2", "song:1"), result.keys.toList())
        assertEquals(listOf("song:2", "song:missing", "song:1"), store.valueReads)
    }

    test("suspending store defaults preserve fields while omitting hash cache misses") {
        val store = RecordingSuspendStore(hashValues = mapOf("songs" to mapOf("2" to "second", "1" to "first")))

        val result = store.getHashValues("songs", listOf("2", "missing", "1"))

        assertEquals(mapOf("2" to "second", "1" to "first"), result)
        assertEquals(listOf("2", "1"), result.keys.toList())
        assertEquals(listOf("songs" to "2", "songs" to "missing", "songs" to "1"), store.hashReads)
    }

    test("suspending store defaults return only requested members present in the set") {
        val store = RecordingSuspendStore(setValues = mapOf("liked" to setOf("2", "1")))

        val result = store.areSetMembers("liked", listOf("2", "missing", "1"))

        assertEquals(setOf("2", "1"), result)
        assertEquals(listOf("2", "1"), result.toList())
        assertEquals(listOf("liked" to "2", "liked" to "missing", "liked" to "1"), store.membershipReads)
    }

    test("suspending refreshing batch reads refresh only values that are present") {
        val expiry = 3.minutes
        val store = RecordingSuspendStore(values = mapOf("song:1" to "first", "song:2" to "second"))

        val result = store.getValuesRefreshingExpire(listOf("song:2", "song:missing", "song:1"), expiry)

        assertEquals(mapOf("song:2" to "second", "song:1" to "first"), result)
        assertEquals(listOf("song:2" to expiry, "song:1" to expiry), store.expiryWrites)
    }

    test("suspending empty batch reads do not call scalar operations") {
        val store = RecordingSuspendStore()

        assertEquals(emptyMap(), store.getValues(emptyList()))
        assertEquals(emptyMap(), store.getHashValues("songs", emptyList()))
        assertEquals(emptySet(), store.areSetMembers("liked", emptyList()))
        assertEquals(emptyMap(), store.getValuesRefreshingExpire(emptyList(), 1.minutes))
        assertEquals(emptyList(), store.allReads)
    }

    test("blocking store defaults preserve keys while omitting string cache misses") {
        val store = RecordingBlockingStore(values = mapOf("song:1" to "first", "song:2" to "second"))

        val result = store.getValues(listOf("song:2", "song:missing", "song:1"))

        assertEquals(mapOf("song:2" to "second", "song:1" to "first"), result)
        assertEquals(listOf("song:2", "song:1"), result.keys.toList())
    }

    test("blocking store defaults preserve fields while omitting hash cache misses") {
        val store = RecordingBlockingStore(hashValues = mapOf("songs" to mapOf("2" to "second", "1" to "first")))

        val result = store.getHashValues("songs", listOf("2", "missing", "1"))

        assertEquals(mapOf("2" to "second", "1" to "first"), result)
        assertEquals(listOf("2", "1"), result.keys.toList())
    }

    test("blocking store defaults return only requested members present in the set") {
        val store = RecordingBlockingStore(setValues = mapOf("liked" to setOf("2", "1")))

        val result = store.areSetMembers("liked", listOf("2", "missing", "1"))

        assertEquals(setOf("2", "1"), result)
        assertEquals(listOf("2", "1"), result.toList())
    }

    test("blocking refreshing batch reads refresh only values that are present") {
        val expiry = 4.minutes
        val store = RecordingBlockingStore(values = mapOf("song:1" to "first", "song:2" to "second"))

        val result = store.getValuesRefreshingExpire(listOf("song:2", "missing", "song:1"), expiry)

        assertEquals(mapOf("song:2" to "second", "song:1" to "first"), result)
        assertEquals(listOf("song:2" to expiry, "song:1" to expiry), store.expiryWrites)
    }

    test("blocking empty batch reads do not call scalar operations") {
        val store = RecordingBlockingStore()

        assertEquals(emptyMap(), store.getValues(emptyList()))
        assertEquals(emptyMap(), store.getHashValues("songs", emptyList()))
        assertEquals(emptySet(), store.areSetMembers("liked", emptyList()))
        assertEquals(emptyMap(), store.getValuesRefreshingExpire(emptyList(), 1.minutes))
        assertEquals(emptyList(), store.allReads)
    }
}

private class RecordingSuspendStore(
    private val values: Map<String, String> = emptyMap(),
    private val hashValues: Map<String, Map<String, String>> = emptyMap(),
    private val setValues: Map<String, Set<String>> = emptyMap(),
) : KacheableStore {
    val valueReads = mutableListOf<String>()
    val hashReads = mutableListOf<Pair<String, String>>()
    val membershipReads = mutableListOf<Pair<String, String>>()
    val expiryWrites = mutableListOf<Pair<String, Duration>>()
    val allReads: List<Any>
        get() = valueReads + hashReads + membershipReads

    override suspend fun get(key: String): String? {
        valueReads += key
        return values[key]
    }

    override suspend fun getHashValue(key: String, field: String): String? {
        hashReads += key to field
        return hashValues[key]?.get(field)
    }

    override suspend fun isSetMember(key: String, member: String): Boolean {
        membershipReads += key to member
        return setValues[key]?.contains(member) == true
    }

    override suspend fun setExpire(key: String, expiry: Duration) {
        expiryWrites += key to expiry
    }

    override suspend fun delete(key: String) = Unit
    override suspend fun deleteHashValue(key: String, field: String) = Unit
    override suspend fun set(key: String, value: String) = Unit
    override suspend fun setHashValue(key: String, field: String, value: String) = Unit
}

private class RecordingBlockingStore(
    private val values: Map<String, String> = emptyMap(),
    private val hashValues: Map<String, Map<String, String>> = emptyMap(),
    private val setValues: Map<String, Set<String>> = emptyMap(),
) : BlockingKacheableStore {
    private val valueReads = mutableListOf<String>()
    private val hashReads = mutableListOf<Pair<String, String>>()
    private val membershipReads = mutableListOf<Pair<String, String>>()
    val expiryWrites = mutableListOf<Pair<String, Duration>>()
    val allReads: List<Any>
        get() = valueReads + hashReads + membershipReads

    override fun get(key: String): String? {
        valueReads += key
        return values[key]
    }

    override fun getHashValue(key: String, field: String): String? {
        hashReads += key to field
        return hashValues[key]?.get(field)
    }

    override fun isSetMember(key: String, member: String): Boolean {
        membershipReads += key to member
        return setValues[key]?.contains(member) == true
    }

    override fun setExpire(key: String, expiry: Duration) {
        expiryWrites += key to expiry
    }

    override fun delete(key: String) = Unit
    override fun deleteHashValue(key: String, field: String) = Unit
    override fun set(key: String, value: String) = Unit
    override fun setHashValue(key: String, field: String, value: String) = Unit
}
