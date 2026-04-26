package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.KacheableStore
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanIterator
import io.lettuce.core.api.StatefulRedisConnection
import kotlin.time.Duration

class RedisBlockingKacheableStore(
    private val conn: StatefulRedisConnection<String, String>,
    private val deleteFromPatternInChunksOf: Int = 20,
) : BlockingKacheableStore {
    override fun delete(key: String) {
        if (!key.contains("*"))
            conn.sync().del(key)
        else {
            val commands = conn.sync()

            ScanIterator.scan(commands, ScanArgs().match(key)).asSequence()
                .chunked(deleteFromPatternInChunksOf)
                .forEach { keys ->
                    if (keys.isNotEmpty()) commands.del(*(keys.toTypedArray()))
                }
        }
    }

    override fun set(key: String, value: String) {
        conn.sync().set(key, value)
    }

    override fun setHashValue(key: String, field: String, value: String) {
        conn.sync().hset(key, field, value)
    }

    override fun get(key: String): String? =
        conn.sync().get(key)

    override fun getHashValue(key: String, field: String): String? =
        conn.sync().hget(key, field)

    override fun deleteHashValue(key: String, field: String) {
        conn.sync().hdel(key, field)
    }

    override fun setExpire(key: String, expiry: Duration) {
        conn.sync().pexpire(key, expiry.inWholeMilliseconds)
    }
}
