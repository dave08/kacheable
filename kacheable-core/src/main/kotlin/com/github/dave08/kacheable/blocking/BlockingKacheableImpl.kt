package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.DefaultGetNameStrategy
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.GetNameStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class BlockingKacheableImpl(
    private val store: BlockingKacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val getNameStrategy: GetNameStrategy,
    private val jsonParser: Json
) : BlockingKacheable {
    /**
     * Don't use * in this list yet... I don't think del supports it properly.
     */
    override fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R {
        keys.forEach { (name, params) ->
            store.delete(getNameStrategy.getName(name, params.toTypedArray()))
        }

        return block()
    }

    override fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R {
        val keyName = getNameStrategy.getName(name, params)
        val result = store.get(keyName)
        val config = configs[name]

        return if (result == null) {
            val blockResult = block()

            val resultToSave = when {
                blockResult == null && config?.nullPlaceholder != null -> config.nullPlaceholder
                blockResult == null || !saveResultIf(blockResult) -> null
                else -> jsonParser.encodeToString(type, blockResult)
            }

            resultToSave?.let {
                store.set(keyName, it)

                if ((config?.expiryType ?: ExpiryType.none) != ExpiryType.none)
                    store.setExpire(keyName, config!!.expiry)
            }

            blockResult
        } else {
            // Set expiry after access
            if (config?.expiryType == ExpiryType.after_access)
                store.setExpire(keyName, config.expiry)

            // Return real null if cached value is equals to placeholder
            if (config?.nullPlaceholder != null && result == config.nullPlaceholder)
                null as R
            else
                jsonParser.decodeFromString(type, result)
        }
    }
}

fun BlockingKacheable(
    store: BlockingKacheableStore,
    configs: Map<String, CacheConfig> = emptyMap(),
    getNameStrategy: GetNameStrategy = DefaultGetNameStrategy,
    jsonParser: Json = Json
) : BlockingKacheable = BlockingKacheableImpl(store, configs, getNameStrategy, jsonParser)