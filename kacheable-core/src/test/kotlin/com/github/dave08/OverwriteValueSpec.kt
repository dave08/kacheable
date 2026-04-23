package com.github.dave08

import com.github.dave08.kacheable.invoke
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val OverwriteValueSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("it keeps the cached value when the same key is requested again") {
            cache("some-cache") {
                "hello"
            }

            cache("some-cache") {
                "world"
            }

            assertEquals("\"hello\"", store.get("some-cache"))
        }
    }
}
