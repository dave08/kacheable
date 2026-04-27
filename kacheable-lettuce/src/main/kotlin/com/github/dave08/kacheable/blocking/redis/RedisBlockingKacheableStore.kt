package com.github.dave08.kacheable.blocking.redis

import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingStoreMutationScope
import com.github.dave08.kacheable.redis.RedisDeleteMode
import io.lettuce.core.GetExArgs
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanIterator
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlin.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RedisBlockingKacheableStore(
    private val conn: StatefulRedisConnection<String, String>,
    private val deleteFromPatternInChunksOf: Int = 20,
    private val deleteScanCount: Long = 1000,
    private val deleteMode: RedisDeleteMode = RedisDeleteMode.Unlink,
) : BlockingKacheableStore {
    private val mutationLock = ReentrantLock()

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

    override fun setValueWithExpire(key: String, value: String, expiry: Duration) {
        conn.sync().psetex(key, expiry.inWholeMilliseconds, value)
    }

    override fun getValueRefreshingExpire(key: String, expiry: Duration): String? =
        try {
            conn.sync().getex(key, GetExArgs.Builder.px(expiry.inWholeMilliseconds))
        } catch (_: RedisCommandExecutionException) {
            conn.sync().get(key)?.also {
                conn.sync().pexpire(key, expiry.inWholeMilliseconds)
            }
        }

    override fun replaceSetMembership(
        member: String,
        membersKey: String,
        nonMembersKey: String,
        isMember: Boolean,
        expiry: Duration?,
        cacheFalse: Boolean,
    ) {
        mutationLock.withLock {
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

    override fun replaceClassifiedMembership(
        member: String,
        targetKey: String,
        candidateKeys: List<String>,
        expiry: Duration?,
    ) {
        mutationLock.withLock {
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

    override fun mutate(block: BlockingStoreMutationScope.() -> Unit) {
        mutationLock.withLock {
            val commands = conn.sync()
            commands.multi()
            try {
                RedisBlockingStoreMutationScope(commands, deleteMode).block()
                commands.exec()
            } catch (t: Throwable) {
                commands.discard()
                throw t
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

private class RedisBlockingStoreMutationScope(
    private val commands: RedisCommands<String, String>,
    private val deleteMode: RedisDeleteMode,
) : BlockingStoreMutationScope {
    override fun delete(key: String) {
        require(!key.contains("*")) { "Pattern deletes are not supported inside atomic Redis mutations." }
        when (deleteMode) {
            RedisDeleteMode.Del -> commands.del(key)
            RedisDeleteMode.Unlink -> commands.unlink(key)
        }
    }

    override fun deleteHashValue(key: String, field: String) {
        commands.hdel(key, field)
    }

    override fun deleteSetMember(key: String, member: String) {
        commands.srem(key, member)
    }

    override fun set(key: String, value: String) {
        commands.set(key, value)
    }

    override fun setHashValue(key: String, field: String, value: String) {
        commands.hset(key, field, value)
    }

    override fun addSetMember(key: String, member: String) {
        commands.sadd(key, member)
    }

    override fun setExpire(key: String, expiry: Duration) {
        commands.pexpire(key, expiry.inWholeMilliseconds)
    }
}
