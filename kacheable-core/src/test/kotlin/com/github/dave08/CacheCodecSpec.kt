package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.*
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.serializer
import kotlin.test.assertEquals

val CacheCodecSpec by testSuite {
    test("one codec can encode cached values and enumerable logical keys") {
        val codec: CacheCodec<Int> = cacheCodec(serializer<Int>())
        val pages = cacheKey(
            "pages", returns<Int>(),
            key = partitioned(keyPart<String>("owner"), enumerableKeyPart("page", codec)),
        )
        val cache = Kacheable(InMemoryKacheableStore(), mapOf(
            "pages" to CacheConfig("pages", partition = CachePartitionPolicy.OnDemand()),
        ))
        val value = cache.invoke("values", codec) { 10 }
        cache(pages("owner", 1)) { value }

        cache(pages("owner", 2)) { _, partition ->
            assertEquals(mapOf(1 to 10), partition.entries())
            20
        }
        assertEquals(10, cache.invoke("values", codec) { error("Expected a cache hit") })
    }

    test("legacy codec names retain source compatibility") {
        val legacy: CacheValueCodec<String> = rawStringCacheValueCodec()
        val shared: CacheCodec<String> = legacy
        assertEquals("unquoted", shared.decode(shared.encode("unquoted")))
    }
}
