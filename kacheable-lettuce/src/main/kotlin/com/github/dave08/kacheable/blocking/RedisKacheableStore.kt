package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.KacheableStore
import io.lettuce.core.api.StatefulRedisConnection
import kotlin.time.Duration

class RedisBlockingKacheableStore(private val conn: StatefulRedisConnection<String, String>) : BlockingKacheableStore {
    override fun delete(key: String) {
        conn.sync().del(key)
    }

    override fun set(key: String, value: String) {
        conn.sync().set(key, value)
    }

    override fun get(key: String): String? =
        conn.sync().get(key)

    override fun setExpire(key: String, expiry: Duration) {
        conn.sync().pexpire(key, expiry.inWholeMilliseconds)
    }
}