package com.github.dave08.kacheable.blocking

import kotlin.time.Duration

interface BlockingKacheableStore {
    fun delete(key: String)

    fun deleteHashValue(key: String, field: String)

    fun set(key: String, value: String)

    fun setHashValue(key: String, field: String, value: String)

    fun get(key: String): String?

    fun getHashValue(key: String, field: String): String?

    fun setExpire(key: String, expiry: Duration)
}
