package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val GuardedPublicationCancellationSpec by testSuite {
    testFixture { GuardedPublicationCancellationFixture() } asContextForEach {
        for (many in listOf(false, true)) {
            val operation = if (many) "selection" else "scalar"
            test("a guarded $operation does not publish a value returned after its loader is cancelled") {
                coroutineScope {
                    val cancelled = async {
                        if (many) cache(pages.many(listOf(1))) { keys, _ ->
                            currentCoroutineContext().cancel()
                            keys.associateWith { "cancelled" }
                        }
                        else mapOf(1 to cache(pages(1)) { _, _ ->
                            currentCoroutineContext().cancel()
                            "cancelled"
                        })
                    }
                    assertFailsWith<CancellationException> { cancelled.await() }
                }

                assertEquals("fresh", cache(pages(1)) { _, _ -> "fresh" })
            }
        }
    }
}

private class GuardedPublicationCancellationFixture {
    val cache = Kacheable(InMemoryKacheableStore(), configs = mapOf("pages" to CacheConfig(
        "pages", partition = CachePartitionPolicy.OnDemand(publication = CachePublication.IfAbsent),
    )))
    val pages = cacheKey("pages", returns<String>(), key = partitioned(key = keyPart<Int>("page")))
}
