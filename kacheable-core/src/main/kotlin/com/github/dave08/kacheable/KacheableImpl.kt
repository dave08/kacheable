package com.github.dave08.kacheable

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class KacheableImpl(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val getNameStrategy: GetNameStrategy,
    private val jsonParser: Json,
) : Kacheable {
    /**
     * Don't use * in this list yet... I don't think del supports it properly.
     */
    override suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R {
        keys.forEach { (name, params) ->
            store.delete(getNameStrategy.getName(name, params.toTypedArray()))
        }

        return block()
    }

    override suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R
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

fun Kacheable(
    store: KacheableStore,
    configs: Map<String, CacheConfig> = emptyMap(),
    getNameStrategy: GetNameStrategy = DefaultGetNameStrategy,
    jsonParser: Json = Json
): Kacheable = KacheableImpl(store, configs, getNameStrategy, jsonParser)