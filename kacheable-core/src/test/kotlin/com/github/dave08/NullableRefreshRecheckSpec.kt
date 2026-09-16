package com.github.dave08

import com.github.dave08.kacheable.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull

val NullableRefreshRecheckSpec by testSuite {
    testFixture { NullableRefreshFixture() } asContextForEach {
        test("refresh loader receives a published null from the coordination recheck") {
            var previous: String? = "not called"

            cache.cache(
                value(1),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = CacheRefreshPolicy.refreshIf { true },
                storeResultIf = { true },
            ) {
                previous = it
                "fresh"
            }

            assertNull(previous)
        }

        test("failed refresh falls back to the published null from the coordination recheck") {
            val result = cache.cache(
                value(1),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = CacheRefreshPolicy.refreshIf { true },
                storeResultIf = { true },
            ) {
                error("refresh failed")
            }

            assertNull(result)
        }

        test("fresh null found during coordination avoids running the refresh loader") {
            var loads = 0

            val result = cache.cache(
                value(1),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = CacheRefreshPolicy.refreshIf { it != null },
                storeResultIf = { true },
            ) {
                loads++
                "unexpected refresh"
            }

            assertNull(result)
            assertEquals(0, loads)
        }
    }
}

private class NullableRefreshFixture {
    val value = cacheKey("nullable-refresh", returns<String?>(), key = exact(keyPart<Int>("id")))
    val cache = Kacheable(
        store = NullOnRecheckStore(),
        configs = mapOf("nullable-refresh" to CacheConfig(
            "nullable-refresh", nullPlaceholder = "<null>",
            resilience = CacheResilienceConfig(singleFlight = SingleFlightMode.Local, maxConcurrentLoads = 1),
        )),
    )
}
