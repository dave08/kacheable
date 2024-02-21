package com.github.dave08.kacheable

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanIterator
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisKacheableStore(
    private val conn: StatefulRedisConnection<String, String>,
    private val deleteFromPatternInChunksOf: Int = 20,
) : KacheableStore {

    override suspend fun delete(key: String) {
        if (!key.contains("*"))
            conn.coroutines().del(key)
        else withContext(Dispatchers.IO) {
            val commands = conn.sync()

            ScanIterator.scan(commands, ScanArgs().match(key)).asSequence()
                .chunked(deleteFromPatternInChunksOf)
                .forEach { keys ->
                    if (keys.isNotEmpty()) commands.del(*(keys.toTypedArray()))
                }
        }
    }

    override suspend fun set(key: String, value: String) {
        conn.coroutines().set(key, value)
    }

    override suspend fun get(key: String): String? = conn.coroutines().get(key)

    override suspend fun setExpire(key: String, expiry: Duration) {
        conn.coroutines().pexpire(key, expiry.inWholeMilliseconds)
    }
}