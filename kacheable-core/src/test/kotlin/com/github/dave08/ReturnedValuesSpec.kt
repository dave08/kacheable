package com.github.dave08

import com.github.dave08.kacheable.invoke
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull

val ReturnedValuesSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("returns the cached value when a new result should not be saved") {
            var result: Boolean? = cache("some-cache", 1, cacheIf = { false }) {
                null
            }

            assertNull(result)

            cache("some-cache", 1) {
                true
            }

            result = cache("some-cache", 1, cacheIf = { false }) {
                null
            }

            assertEquals(result, true)
        }
    }
}
