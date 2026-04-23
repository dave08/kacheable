package com.github.dave08

import com.github.dave08.kacheable.invoke
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val SerializationSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("strings are cached and returned correctly") {
            suspend fun loadValue(): String = cache("some-cache") {
                "hello"
            }

            loadValue()
            val result = loadValue()

            assertEquals("\"hello\"", store.get("some-cache"))
            assertEquals("hello", result)
        }
    }
}
