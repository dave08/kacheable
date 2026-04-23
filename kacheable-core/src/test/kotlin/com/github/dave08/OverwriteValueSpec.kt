package com.github.dave08

import com.github.dave08.kacheable.InMemoryKacheableStore
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.invoke
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val OverwriteValueSpec by testSuite {
    test("it keeps the cached value when the same key is requested again") {
        val store = InMemoryKacheableStore()
        val cache = Kacheable(store)

        cache.invoke("some-cache") {
            "hello"
        }

        cache.invoke("some-cache") {
            "world"
        }

        assertEquals("\"hello\"", store.get("some-cache"))
    }
}
