package com.github.dave08

import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.cache
import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.invoke

class RawFoo(private val cache: Kacheable) {
    var timesCalled: Int = 0

    suspend fun bar() = cache("foo") {
        timesCalled++
        TestSong(32, "something")
    }

    suspend fun nullBar(): TestSong? = cache("foo") { null }

    suspend fun primitiveInt(): Int = cache("foo") { 32 }

    suspend fun primitiveNullInt(): Int? = cache("foo") { null }

    suspend fun primitiveBoolean(): Boolean = cache("foo") { true }

    suspend fun setOfInts(): Set<Int> = cache("foo") { setOf(1, 2, 3) }

    suspend fun dontSaveBar(shouldSave: Boolean = false): TestSong =
        cache("foo", cacheIf = { shouldSave }) {
            TestSong(32, "something")
        }

    suspend fun baz(id: Int, name: String) = cache("foo", id, name) {
        TestSong(32, "something")
    }

    suspend fun invBar() = cache.invalidate("foo" to emptyList()) {}

    suspend fun invBaz(id: Int, name: String) = cache.invalidate("foo" to listOf(id, name)) {}
}

class BlockingRawFoo(private val cache: BlockingKacheable) {
    var timesCalled: Int = 0

    fun bar() = cache("BlockingFoo") {
        timesCalled++
        TestSong(32, "something")
    }

    fun nullBar(): TestSong? = cache("BlockingFoo") { null }

    fun primitiveInt(): Int = cache("BlockingFoo") { 32 }

    fun primitiveNullInt(): Int? = cache("BlockingFoo") { null }

    fun primitiveBoolean(): Boolean = cache("BlockingFoo") { true }

    fun setOfInts(): Set<Int> = cache("BlockingFoo") { setOf(1, 2, 3) }

    fun dontSaveBar(shouldSave: Boolean = false): TestSong =
        cache("BlockingFoo", cacheIf = { shouldSave }) {
            TestSong(32, "something")
        }

    fun baz(id: Int, name: String) = cache("BlockingFoo", id, name) {
        TestSong(32, "something")
    }

    fun invBar() = cache.invalidate("BlockingFoo" to emptyList()) {}

    fun invBaz(id: Int, name: String) = cache.invalidate("BlockingFoo" to listOf(id, name)) {}
}
