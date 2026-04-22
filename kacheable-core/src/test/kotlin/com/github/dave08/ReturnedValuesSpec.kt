package com.github.dave08

import com.github.dave08.kacheable.InMemoryKacheableStore
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.invoke
import io.kotest.core.spec.style.FreeSpec
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import strikt.assertions.isNullOrEmpty
import strikt.assertions.isTrue

class ReturnedValuesSpec : FreeSpec({
    val store = InMemoryKacheableStore()
    val kacheable = Kacheable(store)

    "only return if value exists in the cache" {
        var result: Boolean? = kacheable.invoke("some-cache", 1, saveResultIf = { false }) {
            null
        }

        expectThat(result).isNull()
        kacheable.invoke("some-cache", 1) {
            true
        }

        result = kacheable.invoke("some-cache", 1, saveResultIf = { false }) {
            null
        }

        expectThat(result).isTrue()
    }
})