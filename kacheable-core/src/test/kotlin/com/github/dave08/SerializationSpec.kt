package com.github.dave08

import com.github.dave08.kacheable.InMemoryKacheableStore
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.invoke
import io.kotest.core.spec.style.FreeSpec
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class SerializationSpec : FreeSpec({
    val store = InMemoryKacheableStore()

    "strings" {
        val cache = Kacheable(store)

        suspend fun test(): String = cache.invoke("some-cache") {
            "hello"
        }
        test()
        val result = test()

        expectThat(store.get("some-cache")).isEqualTo("\"hello\"")
        expectThat(result).isEqualTo("hello")
    }
})