package com.github.dave08

import com.github.dave08.kacheable.InMemoryKacheableStore
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.invoke
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val SerializationSpec by testSuite {
    test("strings are cached and returned correctly") {
        val store = InMemoryKacheableStore()
        val cache = Kacheable(store)

        suspend fun loadValue(): String = cache.invoke("some-cache") {
            "hello"
        }

        loadValue()
        val result = loadValue()

        assertEquals("\"hello\"", store.get("some-cache"))
        assertEquals("hello", result)
    }
}
