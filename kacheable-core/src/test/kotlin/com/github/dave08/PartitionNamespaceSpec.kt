package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.cache
import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val PartitionNamespaceSpec by testSuite {
    testFixture { PartitionNamespaceFixture() } asContextForEach {
        test("ordinary and guarded hashes with identical logical names retain independent values") {
            ordinary.cache(pages("a", 1)) { "ordinary" }
            guarded.cache(pages("a", 1)) { "guarded" }

            assertEquals("ordinary", ordinary.cache(pages("a", 1)) { error("Missing ordinary value") })
            assertEquals("guarded", guarded.cache(pages("a", 1)) { error("Missing guarded value") })
        }

        test("guarded partition invalidation preserves ordinary homonyms and other partitions") {
            seedBoth("a")
            seedBoth("b")

            guarded.invalidate(pages.partition("a"))

            assertEquals("replacement", guarded.cache(pages("a", 1)) { "replacement" })
            assertEquals("guarded-b", guarded.cache(pages("b", 1)) { error("Unrelated partition removed") })
            assertEquals("ordinary-a", ordinary.cache(pages("a", 1)) { error("Ordinary homonym removed") })
        }

        test("guarded family invalidation preserves ordinary homonyms and another guarded family") {
            seedBoth("a")
            seedBoth("b")
            guarded.cache(otherPages("a", 1)) { "other" }

            guarded.invalidate(pages.all())

            assertEquals("new-a", guarded.cache(pages("a", 1)) { "new-a" })
            assertEquals("new-b", guarded.cache(pages("b", 1)) { "new-b" })
            assertEquals("ordinary-a", ordinary.cache(pages("a", 1)) { error("Ordinary family removed") })
            assertEquals("other", guarded.cache(otherPages("a", 1)) { error("Other guarded family removed") })
        }

        test("ordinary partition invalidation preserves guarded homonyms and other ordinary partitions") {
            seedBoth("a")
            seedBoth("b")

            ordinary.invalidate(pages.partition("a"))

            assertEquals("new", ordinary.cache(pages("a", 1)) { "new" })
            assertEquals("guarded-a", guarded.cache(pages("a", 1)) { error("Guarded homonym removed") })
            assertEquals("ordinary-b", ordinary.cache(pages("b", 1)) { error("Other ordinary partition removed") })
        }

        test("ordinary family invalidation preserves guarded homonyms") {
            seedBoth("a")

            ordinary.invalidate(pages.all())

            assertEquals("replacement", ordinary.cache(pages("a", 1)) { "replacement" })
            assertEquals("guarded-a", guarded.cache(pages("a", 1)) { error("Guarded homonym removed") })
        }
    }

    test("guarded invalidation honors custom naming and hash tags") {
        val fixture = PartitionNamespaceFixture(defaultCacheNamingStrategy(
            primaryKeyCombiner = { name, values -> "tenant:{$name}:${values.joinToString("/")}" },
        ))
        fixture.seedBoth("a")

        fixture.guarded.invalidate(fixture.pages.partition("a"))

        assertEquals("new", fixture.guarded.cache(fixture.pages("a", 1)) { "new" })
        assertEquals("ordinary-a", fixture.ordinary.cache(fixture.pages("a", 1)) { error("Ordinary custom name removed") })
    }

    test("guarded family invalidation honors custom naming and hash tags") {
        val fixture = PartitionNamespaceFixture(defaultCacheNamingStrategy(
            primaryKeyCombiner = { name, values -> "tenant:{$name}:${values.joinToString("/")}" },
        ))
        fixture.seedBoth("a")
        fixture.seedBoth("b")

        fixture.guarded.invalidate(fixture.pages.all())

        assertEquals("new-a", fixture.guarded.cache(fixture.pages("a", 1)) { "new-a" })
        assertEquals("new-b", fixture.guarded.cache(fixture.pages("b", 1)) { "new-b" })
        assertEquals("ordinary-a", fixture.ordinary.cache(fixture.pages("a", 1)) { error("Ordinary custom name removed") })
    }

    test("guarded family invalidation does not invent a missing outer partition for naming") {
        val fixture = PartitionNamespaceFixture(defaultCacheNamingStrategy(
            primaryKeyCombiner = { name, values -> "tenant:{$name}:${values.single()}" },
        ))
        fixture.seedBoth("a")
        fixture.seedBoth("b")

        fixture.guarded.invalidate(fixture.pages.all())

        assertEquals("new-a", fixture.guarded.cache(fixture.pages("a", 1)) { "new-a" })
        assertEquals("new-b", fixture.guarded.cache(fixture.pages("b", 1)) { "new-b" })
        assertEquals("ordinary-a", fixture.ordinary.cache(fixture.pages("a", 1)) { error("Ordinary custom name removed") })
    }

    test("guarded family invalidation includes keys without an explicit partition") {
        val fixture = PartitionNamespaceFixture()
        val pages = cacheKey("pages", returns<String>(), key = partitioned(key = keyPart<Int>("page")))
        fixture.guarded.cache(pages(1)) { "old" }

        fixture.guarded.invalidate(pages.all())

        assertEquals("new", fixture.guarded.cache(pages(1)) { "new" })
    }

    test("guarded single partition family invalidation preserves root-only custom naming") {
        val fixture = PartitionNamespaceFixture(defaultCacheNamingStrategy(
            primaryKeyCombiner = { name, values ->
                require(values.isEmpty()) { "This cache family has no outer partition" }
                "tenant:{$name}"
            },
        ))
        val pages = cacheKey("pages", returns<String>(), key = partitioned(key = keyPart<Int>("page")))
        fixture.guarded.cache(pages(1)) { "old" }

        fixture.guarded.invalidate(pages.all())

        assertEquals("new", fixture.guarded.cache(pages(1)) { "new" })
    }

    test("guarded partition invalidation includes keys without an explicit partition") {
        val fixture = PartitionNamespaceFixture()
        val pages = cacheKey("pages", returns<String>(), key = partitioned(key = keyPart<Int>("page")))
        fixture.guarded.cache(pages(1)) { "old" }

        fixture.guarded.invalidate(pages.partition())

        assertEquals("new", fixture.guarded.cache(pages(1)) { "new" })
    }

    test("blocking guarded family invalidation preserves ordinary homonyms") {
        val store = BlockingPartitionTestStore()
        val ordinary = BlockingKacheable(store)
        val guarded = BlockingKacheable(store, configs = partitionNamespaceConfigs)
        val pages = namespacePages("pages")
        ordinary.cache(pages("a", 1)) { "ordinary" }
        guarded.cache(pages("a", 1)) { "guarded" }

        guarded.invalidate(pages.all())

        assertEquals("new", guarded.cache(pages("a", 1)) { "new" })
        assertEquals("ordinary", ordinary.cache(pages("a", 1)) { error("Ordinary homonym removed") })
    }
}

private val partitionNamespaceConfigs = listOf("pages", "other").associateWith {
    CacheConfig(it, partition = CachePartitionPolicy.OnDemand())
}

private fun namespacePages(name: String) = cacheKey(
    name, returns<String>(),
    key = partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")),
)

private class PartitionNamespaceFixture(naming: CacheNamingStrategy = defaultCacheNamingStrategy()) {
    private val store = InMemoryKacheableStore()
    val ordinary = Kacheable(store, namingStrategy = naming)
    val guarded = Kacheable(store, configs = partitionNamespaceConfigs, namingStrategy = naming)
    val pages = namespacePages("pages")
    val otherPages = namespacePages("other")

    suspend fun seedBoth(group: String) {
        ordinary.cache(pages(group, 1)) { "ordinary-$group" }
        guarded.cache(pages(group, 1)) { "guarded-$group" }
    }
}
