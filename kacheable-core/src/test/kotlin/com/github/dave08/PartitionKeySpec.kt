package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import com.github.dave08.kacheable.store.cacheValueCodec
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals

val PartitionKeySpec by testSuite {
    testFixture { PartitionKeyFixture() } asContextForEach {
        test("partition key enumeration preserves Long keys across cache runtimes") {
            val pages = cacheKey("pages", returns<String>(), key = partitioned(owner, enumerableKeyPart<Long>("page")))
            cache(pages("one", 42L)) { "stored" }

            anotherCache()(pages("one", 99L)) { _, partition ->
                assertEquals(setOf(42L), partition.keys())
                assertEquals(mapOf(42L to "stored"), partition.entries())
                "next"
            }
        }

        test("custom partition keys are decoded with their codec rather than physical key segments") {
            val pageKey = enumerableKeyPart("page", cacheValueCodec(Window.serializer()), Window::start, Window::size)
            val pages = cacheKey("pages", returns<String>(), key = partitioned(owner, pageKey))
            val first = Window(0, 10)
            cache(pages("one", first)) { "first" }

            anotherCache()(pages("one", Window(10, 10))) { _, partition ->
                assertEquals(setOf(first), partition.keys())
                assertEquals(CacheEntry.Present("first"), partition.entry(first))
                "second"
            }
        }

        test("nullable partition keys round trip independently of missing entries") {
            val pages = cacheKey("pages", returns<String>(), key = partitioned(owner, enumerableKeyPart<Int?>("page")))
            cache(pages("one", null)) { "default" }

            anotherCache()(pages("one", 1)) { _, partition ->
                assertEquals(setOf<Int?>(null), partition.keys())
                assertEquals(CacheEntry.Present("default"), partition.entry(null))
                "one"
            }
        }

        test("reified domain keys retain their original value independently of extracted segments") {
            val pageKey = enumerableKeyPart<Window>("page", Window::start, Window::size)
            val pages = cacheKey("pages", returns<String>(), key = partitioned(owner, pageKey))
            val first = Window(0, 10)
            cache(pages("one", first)) { "first" }

            anotherCache()(pages("one", Window(10, 10))) { _, partition ->
                assertEquals(setOf(first), partition.keys())
                "second"
            }
        }

        test("enumeration is retained when outer partition parts are composed") {
            val pages = cacheKey("pages", returns<String>(), key = partitioned(
                owner + keyPart<Int>("version"), enumerableKeyPart<Int>("page"),
            ))
            cache(pages("one", 3, 0)) { "first" }

            anotherCache()(pages("one", 3, 1)) { page, partition ->
                assertEquals(1, page)
                assertEquals(setOf(0), partition.keys())
                "second"
            }
        }

        test("delegated enumerable key parts preserve their capability and property name") {
            val page by enumerableKeyPart<Int>()
            val pages = cacheKey("pages", returns<String>(), key = partitioned(owner, page))
            cache(pages("one", 0)) { "first" }

            anotherCache()(pages("one", 1)) { _, partition ->
                assertEquals("page", page.name)
                assertEquals(setOf(0), partition.keys())
                "second"
            }
        }

    }
}

private class PartitionKeyFixture {
    val owner = keyPart<String>("owner")
    private val store = InMemoryKacheableStore()
    private val configs = mapOf("pages" to CacheConfig("pages", partition = CachePartitionPolicy.OnDemand()))
    val cache = anotherCache()
    fun anotherCache(): Kacheable = Kacheable(store, configs)
}

@Serializable
private data class Window(val start: Int, val size: Int)
