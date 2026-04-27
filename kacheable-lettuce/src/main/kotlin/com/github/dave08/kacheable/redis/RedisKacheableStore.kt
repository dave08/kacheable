package com.github.dave08.kacheable.redis

import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.StoreMutationScope
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.GetExArgs
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanIterator
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisKacheableStore(
    private val conn: StatefulRedisConnection<String, String>,
    private val deleteFromPatternInChunksOf: Int = 20,
    private val deleteScanCount: Long = 1000,
    private val deleteMode: RedisDeleteMode = RedisDeleteMode.Unlink,
) : KacheableStore {
    private val mutationMutex = Mutex()

    override suspend fun delete(key: String) {
        if (!key.contains("*"))
            deleteKeys(key)
        else withContext(Dispatchers.IO) {
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

    private suspend fun deleteKeys(vararg keys: String) {
        when (deleteMode) {
            RedisDeleteMode.Del -> conn.coroutines().del(*keys)
            RedisDeleteMode.Unlink -> conn.coroutines().unlink(*keys)
        }
    }

    override suspend fun set(key: String, value: String) {
        conn.coroutines().set(key, value)
    }

    override suspend fun setHashValue(key: String, field: String, value: String) {
        conn.coroutines().hset(key, field, value)
    }

    override suspend fun get(key: String): String? = conn.coroutines().get(key)

    override suspend fun getHashValue(key: String, field: String): String? = conn.coroutines().hget(key, field)

    override suspend fun deleteHashValue(key: String, field: String) {
        conn.coroutines().hdel(key, field)
    }

    override suspend fun deleteSetMember(key: String, member: String) {
        conn.coroutines().srem(key, member)
    }

    override suspend fun addSetMember(key: String, member: String) {
        conn.coroutines().sadd(key, member)
    }

    override suspend fun isSetMember(key: String, member: String): Boolean =
        conn.coroutines().sismember(key, member) == true

    override suspend fun setExpire(key: String, expiry: Duration) {
        conn.coroutines().pexpire(key, expiry.inWholeMilliseconds)
    }

    override suspend fun setValueWithExpire(key: String, value: String, expiry: Duration) {
        conn.coroutines().psetex(key, expiry.inWholeMilliseconds, value)
    }

    override suspend fun getValueRefreshingExpire(key: String, expiry: Duration): String? =
        withContext(Dispatchers.IO) {
            try {
                conn.sync().getex(key, GetExArgs.Builder.px(expiry.inWholeMilliseconds))
            } catch (_: RedisCommandExecutionException) {
                conn.sync().get(key)?.also {
                    conn.sync().pexpire(key, expiry.inWholeMilliseconds)
                }
            }
        }

    override suspend fun replaceSetMembership(
        member: String,
        membersKey: String,
        nonMembersKey: String,
        isMember: Boolean,
        expiry: Duration?,
        cacheFalse: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            mutationMutex.withLock {
                val targetIndex =
                    when {
                        isMember -> "1"
                        cacheFalse -> "2"
                        else -> "0"
                    }
                conn.sync().eval<Long>(
                    REPLACE_SET_MEMBERSHIP_SCRIPT,
                    ScriptOutputType.INTEGER,
                    arrayOf(membersKey, nonMembersKey),
                    member,
                    targetIndex,
                    expiry?.inWholeMilliseconds?.toString().orEmpty(),
                )
            }
        }
    }

    override suspend fun replaceClassifiedMembership(
        member: String,
        targetKey: String,
        candidateKeys: List<String>,
        expiry: Duration?,
    ) {
        withContext(Dispatchers.IO) {
            mutationMutex.withLock {
                val keys = listOf(targetKey) + candidateKeys.filterNot { it == targetKey }
                conn.sync().eval<Long>(
                    REPLACE_CLASSIFIED_MEMBERSHIP_SCRIPT,
                    ScriptOutputType.INTEGER,
                    keys.toTypedArray(),
                    member,
                    expiry?.inWholeMilliseconds?.toString().orEmpty(),
                )
            }
        }
    }

    override suspend fun mutate(block: suspend StoreMutationScope.() -> Unit) {
        withContext(Dispatchers.IO) {
            mutationMutex.withLock {
                val commands = conn.sync()
                commands.multi()
                try {
                    RedisStoreMutationScope(commands, deleteMode).block()
                    commands.exec()
                } catch (t: Throwable) {
                    commands.discard()
                    throw t
                }
            }
        }
    }
}

private const val REPLACE_SET_MEMBERSHIP_SCRIPT = """
redis.call('SREM', KEYS[1], ARGV[1])
redis.call('SREM', KEYS[2], ARGV[1])
local targetIndex = tonumber(ARGV[2])
if targetIndex ~= nil and targetIndex > 0 then
  redis.call('SADD', KEYS[targetIndex], ARGV[1])
  if ARGV[3] ~= '' then
    redis.call('PEXPIRE', KEYS[targetIndex], ARGV[3])
  end
end
return 1
"""

private const val REPLACE_CLASSIFIED_MEMBERSHIP_SCRIPT = """
for i = 1, #KEYS do
  redis.call('SREM', KEYS[i], ARGV[1])
end
redis.call('SADD', KEYS[1], ARGV[1])
if ARGV[2] ~= '' then
  redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
return 1
"""

private class RedisStoreMutationScope(
    private val commands: RedisCommands<String, String>,
    private val deleteMode: RedisDeleteMode,
) : StoreMutationScope {
    override suspend fun delete(key: String) {
        require(!key.contains("*")) { "Pattern deletes are not supported inside atomic Redis mutations." }
        when (deleteMode) {
            RedisDeleteMode.Del -> commands.del(key)
            RedisDeleteMode.Unlink -> commands.unlink(key)
        }
    }

    override suspend fun deleteHashValue(key: String, field: String) {
        commands.hdel(key, field)
    }

    override suspend fun deleteSetMember(key: String, member: String) {
        commands.srem(key, member)
    }

    override suspend fun set(key: String, value: String) {
        commands.set(key, value)
    }

    override suspend fun setHashValue(key: String, field: String, value: String) {
        commands.hset(key, field, value)
    }

    override suspend fun addSetMember(key: String, member: String) {
        commands.sadd(key, member)
    }

    override suspend fun setExpire(key: String, expiry: Duration) {
        commands.pexpire(key, expiry.inWholeMilliseconds)
    }
}
