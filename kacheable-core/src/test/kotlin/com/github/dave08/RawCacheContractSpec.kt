package com.github.dave08

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.ExpiryType
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.delay
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private interface RawCacheRobot {
    val timesCalled: Int

    suspend fun bar(): TestSong
    suspend fun nullBar(): TestSong?
    suspend fun primitiveInt(): Int
    suspend fun primitiveNullInt(): Int?
    suspend fun primitiveBoolean(): Boolean
    suspend fun setOfInts(): Set<Int>
    suspend fun dontSaveBar(shouldSave: Boolean = false): TestSong
    suspend fun baz(id: Int, name: String): TestSong
    suspend fun invBar()
    suspend fun invBaz(id: Int, name: String)
}

private interface RawCacheContractFixture {
    val cacheName: String
    val multiParameterKey: String
    val stringMap: MutableMap<String, String>
    val expireCalls: List<Pair<String, Duration>>
    val subject: RawCacheRobot
}

private class SuspendRawCacheRobot(
    private val delegate: RawFoo,
) : RawCacheRobot {
    override val timesCalled: Int
        get() = delegate.timesCalled

    override suspend fun bar(): TestSong = delegate.bar()
    override suspend fun nullBar(): TestSong? = delegate.nullBar()
    override suspend fun primitiveInt(): Int = delegate.primitiveInt()
    override suspend fun primitiveNullInt(): Int? = delegate.primitiveNullInt()
    override suspend fun primitiveBoolean(): Boolean = delegate.primitiveBoolean()
    override suspend fun setOfInts(): Set<Int> = delegate.setOfInts()
    override suspend fun dontSaveBar(shouldSave: Boolean): TestSong = delegate.dontSaveBar(shouldSave)
    override suspend fun baz(id: Int, name: String): TestSong = delegate.baz(id, name)
    override suspend fun invBar() = delegate.invBar()
    override suspend fun invBaz(id: Int, name: String) = delegate.invBaz(id, name)
}

private class BlockingRawCacheRobot(
    private val delegate: BlockingRawFoo,
) : RawCacheRobot {
    override val timesCalled: Int
        get() = delegate.timesCalled

    override suspend fun bar(): TestSong = delegate.bar()
    override suspend fun nullBar(): TestSong? = delegate.nullBar()
    override suspend fun primitiveInt(): Int = delegate.primitiveInt()
    override suspend fun primitiveNullInt(): Int? = delegate.primitiveNullInt()
    override suspend fun primitiveBoolean(): Boolean = delegate.primitiveBoolean()
    override suspend fun setOfInts(): Set<Int> = delegate.setOfInts()
    override suspend fun dontSaveBar(shouldSave: Boolean): TestSong = delegate.dontSaveBar(shouldSave)
    override suspend fun baz(id: Int, name: String): TestSong = delegate.baz(id, name)
    override suspend fun invBar() = delegate.invBar()
    override suspend fun invBaz(id: Int, name: String) = delegate.invBaz(id, name)
}

private data class RawCacheMode(
    val createFixture: (Array<out CacheConfig>) -> RawCacheContractFixture,
    val pauseAfterAccess: suspend () -> Unit,
)

private fun rawCacheContractSpec(mode: RawCacheMode) = testSuite {
    test("saves the result of a function with no parameters") {
        val fixture = mode.createFixture(emptyArray())
        val results = mutableListOf<TestSong>()
        repeat(5) {
            results += fixture.subject.bar()
        }

        expect {
            that(fixture.subject.timesCalled).isEqualTo(1)
            that(fixture.stringMap[fixture.cacheName]).isEqualTo("""{"id":32,"name":"something"}""")
            that(results).all { isEqualTo(TestSong(32, "something")) }
        }
    }

    test("saves the result of a function with multiple parameters") {
        val fixture = mode.createFixture(emptyArray())

        fixture.subject.baz(32, "something")

        expectThat(fixture.stringMap.keys).containsExactly(fixture.multiParameterKey)
    }

    test("sets expiry from last write") {
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo", ExpiryType.after_write, 30.minutes)))

        fixture.subject.bar()

        expectThat(fixture.expireCalls).containsExactly(fixture.cacheName to 30.minutes)
    }

    test("saves cache with default configs when not specified") {
        val fixture = mode.createFixture(emptyArray())

        fixture.subject.bar()

        expectThat(fixture.stringMap.containsKey(fixture.cacheName)).isEqualTo(true)
    }

    test("sets expiry from last access") {
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo", ExpiryType.after_access, 30.minutes)))

        fixture.subject.bar()
        mode.pauseAfterAccess()
        fixture.subject.bar()

        expectThat(fixture.expireCalls).containsExactly(
            fixture.cacheName to 30.minutes,
            fixture.cacheName to 30.minutes,
        )
    }

    test("does not save an entry when the null placeholder is not configured") {
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo", nullPlaceholder = null)))

        fixture.subject.nullBar()

        expectThat(fixture.stringMap.keys).isEmpty()
    }

    test("stores the placeholder when the null placeholder is configured") {
        val placeholder = "--placeholder--"
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo", nullPlaceholder = placeholder)))

        fixture.subject.nullBar()

        expectThat(fixture.stringMap[fixture.cacheName]).isEqualTo(placeholder)
    }

    test("returns null when the cached value is the placeholder") {
        val placeholder = "--placeholder--"
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo", nullPlaceholder = placeholder)))

        fixture.subject.nullBar()
        val result = fixture.subject.nullBar()

        expectThat(result).isNull()
    }

    test("invalidates a cache entry without parameters") {
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo")))

        fixture.subject.bar()
        fixture.subject.invBar()

        expectThat(fixture.stringMap.containsKey(fixture.cacheName)).isEqualTo(false)
    }

    test("invalidates a cache entry with matching parameters") {
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo")))

        fixture.subject.baz(32, "something")
        fixture.subject.invBaz(32, "something")

        expectThat(fixture.stringMap.containsKey(fixture.multiParameterKey)).isEqualTo(false)
    }

    test("saveResultIf controls whether the result is cached") {
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo")))

        fixture.subject.dontSaveBar()
        val result = fixture.subject.dontSaveBar()

        expect {
            that(result).isEqualTo(TestSong(32, "something"))
            that(fixture.stringMap.keys).isEmpty()
        }

        fixture.subject.dontSaveBar(true)
        val result2 = fixture.subject.dontSaveBar(true)

        expect {
            that(result2).isEqualTo(TestSong(32, "something"))
            that(fixture.stringMap.keys).containsExactly(fixture.cacheName)
        }
    }

    test("caches an int result") {
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo")))

        fixture.subject.primitiveInt()

        expectThat(fixture.stringMap[fixture.cacheName]).isEqualTo("32")
    }

    test("caches a null int using the placeholder") {
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo", nullPlaceholder = "null")))

        fixture.subject.primitiveNullInt()

        expectThat(fixture.stringMap[fixture.cacheName]).isEqualTo("null")
    }

    test("caches a boolean result") {
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo")))

        fixture.subject.primitiveBoolean()

        expectThat(fixture.stringMap[fixture.cacheName]).isEqualTo("true")
    }

    test("caches a set result") {
        val fixture = mode.createFixture(arrayOf(CacheConfig("foo")))

        fixture.subject.setOfInts()

        expectThat(fixture.stringMap[fixture.cacheName]).isEqualTo("[1,2,3]")
    }
}

private val suspendRawCacheMode = RawCacheMode(
    createFixture = { configs ->
        val fixture = SuspendCacheFixture()
        val cache = if (configs.isEmpty()) {
            fixture.cache
        } else {
            com.github.dave08.kacheable.Kacheable(fixture.store, configs.associateBy { it.name })
        }
        val subject = RawFoo(cache)
        object : RawCacheContractFixture {
            override val cacheName: String = "foo"
            override val multiParameterKey: String = "foo:32,something"
            override val stringMap: MutableMap<String, String> = fixture.store.map
            override val expireCalls: List<Pair<String, Duration>> = fixture.store.expireCalls
            override val subject: RawCacheRobot = SuspendRawCacheRobot(subject)
        }
    },
    pauseAfterAccess = { delay(1) },
)

private val blockingRawCacheMode = RawCacheMode(
    createFixture = { configs ->
        val fixture = BlockingCacheFixture()
        val blockingConfigs = configs.map { config ->
            if (config.name == "foo") config.copy(name = "BlockingFoo") else config
        }.toTypedArray()
        val cache = if (blockingConfigs.isEmpty()) {
            fixture.cache
        } else {
            com.github.dave08.kacheable.blocking.BlockingKacheable(
                fixture.store,
                blockingConfigs.associateBy { it.name },
            )
        }
        val subject = BlockingRawFoo(cache)
        object : RawCacheContractFixture {
            override val cacheName: String = "BlockingFoo"
            override val multiParameterKey: String = "BlockingFoo:32,something"
            override val stringMap: MutableMap<String, String> = fixture.store.map
            override val expireCalls: List<Pair<String, Duration>> = fixture.store.expireCalls
            override val subject: RawCacheRobot = BlockingRawCacheRobot(subject)
        }
    },
    pauseAfterAccess = { Thread.sleep(1) },
)

val RawCacheContractSpec by rawCacheContractSpec(suspendRawCacheMode)

val BlockingRawCacheContractSpec by rawCacheContractSpec(blockingRawCacheMode)
