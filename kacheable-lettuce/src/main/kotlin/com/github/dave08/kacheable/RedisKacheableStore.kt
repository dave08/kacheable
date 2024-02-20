package com.github.dave08.kacheable

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlin.time.Duration

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisKacheableStore(private val conn: StatefulRedisConnection<String, String>) : KacheableStore {

    override suspend fun delete(key: String) {
        conn.coroutines().del(key)
    }

    override suspend fun set(key: String, value: String) {
        conn.coroutines().set(key, value)
    }

    override suspend fun get(key: String): String? = conn.coroutines().get(key)

    override suspend fun setExpire(key: String, expiry: Duration) {
        conn.coroutines().pexpire(key, expiry.inWholeMilliseconds)
    }
}