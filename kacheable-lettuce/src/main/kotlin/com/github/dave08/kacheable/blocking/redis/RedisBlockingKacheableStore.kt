package com.github.dave08.kacheable.blocking.redis

import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisDeleteMode
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanIterator
import io.lettuce.core.api.StatefulRedisConnection
import kotlin.time.Duration

class RedisBlockingKacheableStore(
    private val conn: StatefulRedisConnection<String, String>,
    private val deleteFromPatternInChunksOf: Int = 20,
    private val deleteScanCount: Long = 1000,
    private val deleteMode: RedisDeleteMode = RedisDeleteMode.Unlink,
) : BlockingKacheableStore {
    override fun delete(key: String) {
        if (!key.contains("*"))
            deleteKeys(key)
        else {
            val commands = conn.sync()

            ScanIterator.scan(commands, ScanArgs().match(key).limit(deleteScanCount)).asSequence()
                .chunked(deleteFromPatternInChunksOf)
                .forEach { keys ->
                    if (keys.isNotEmpty()) {
                        when (deleteMode) {
                            RedisDeleteMode.Del -> commands.del(*(keys.toTypedArray()))
                            RedisDeleteMode.Unlink -> commands.unlink(*(keys.toTypedArray()))
                        }
                    }
                }
        }
    }

    private fun deleteKeys(vararg keys: String) {
        when (deleteMode) {
            RedisDeleteMode.Del -> conn.sync().del(*keys)
            RedisDeleteMode.Unlink -> conn.sync().unlink(*keys)
        }
    }

    override fun set(key: String, value: String) {
        conn.sync().set(key, value)
    }

    override fun setHashValue(key: String, field: String, value: String) {
        conn.sync().hset(key, field, value)
    }

    override fun get(key: String): String? = conn.sync().get(key)

    override fun getHashValue(key: String, field: String): String? = conn.sync().hget(key, field)

    override fun deleteHashValue(key: String, field: String) {
        conn.sync().hdel(key, field)
    }

    override fun deleteSetMember(key: String, member: String) {
        conn.sync().srem(key, member)
    }

    override fun addSetMember(key: String, member: String) {
        conn.sync().sadd(key, member)
    }

    override fun isSetMember(key: String, member: String): Boolean = conn.sync().sismember(key, member)

    override fun setExpire(key: String, expiry: Duration) {
        conn.sync().pexpire(key, expiry.inWholeMilliseconds)
    }
}
