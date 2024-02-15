package com.github.dave08.kacheable

import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import kotlin.time.Duration

class RedisKacheableStore(private val conn: StatefulRedisConnection<String, String>) : KacheableStore {
    override suspend fun delete(key: String) {
        conn.async().del(key).await()
    }

    override suspend fun set(key: String, value: String) {
        conn.async().set(key, value).await()
    }

    override suspend fun get(key: String): String? =
        conn.async().get(key).await()

    override suspend fun setExpire(key: String, expiry: Duration) {
        conn.async().pexpire(key, expiry.inWholeMilliseconds).await()
    }
}