package com.github.dave08

import com.github.dave08.kacheable.InMemoryKacheableStore
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.invoke
import io.kotest.core.spec.style.FreeSpec
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class OverwriteValueSpec : FreeSpec() {
    init {
        "!it overwrites an existing value when trying to save it again" {
            val store = InMemoryKacheableStore()
            val cache = Kacheable(store)

            cache.invoke("some-cache") {
                "hello"
            }

            cache.invoke("some-cache") {
                "world"
            }

            expectThat(store.get("some-cache")).isEqualTo("\"world\"")
        }
    }
}