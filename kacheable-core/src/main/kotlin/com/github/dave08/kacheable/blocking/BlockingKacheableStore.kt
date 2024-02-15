package com.github.dave08.kacheable.blocking

import kotlin.time.Duration

interface BlockingKacheableStore {
    fun delete(key: String)

    fun set(key: String, value: String)

    fun get(key: String): String?

    fun setExpire(key: String, expiry: Duration)
}