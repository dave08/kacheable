package com.github.dave08

import com.github.dave08.kacheable.InMemoryKacheableStore
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.invoke
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertNull
import kotlin.test.assertTrue

val ReturnedValuesSpec by testSuite {
    test("returns the cached value when a new result should not be saved") {
        val store = InMemoryKacheableStore()
        val kacheable = Kacheable(store)

        var result: Boolean? = kacheable.invoke("some-cache", 1, saveResultIf = { false }) {
            null
        }

        assertNull(result)

        kacheable.invoke("some-cache", 1) {
            true
        }

        result = kacheable.invoke("some-cache", 1, saveResultIf = { false }) {
            null
        }

        assertTrue(result == true)
    }
}
