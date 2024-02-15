package com.github.dave08.kacheable

import kotlin.time.Duration

interface KacheableStore {
    suspend fun delete(key: String)

    suspend fun set(key: String, value: String)

    suspend fun get(key: String): String?

    suspend fun setExpire(key: String, expiry: Duration)
}