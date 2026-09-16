package kacheable

import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.reactive.RedisReactiveCommands
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** Performs one real competing write at the boundary before the scan's next Redis page command. */
internal class RedisHashConversionBeforePageRead(
    delegate: StatefulRedisConnection<String, String>,
    convert: () -> Unit,
) {
    var converted = false
        private set

    private val commands = intercept<RedisReactiveCommands<String, String>>(delegate.reactive()) { method ->
        if (!converted && method in setOf("hscan", "evalsha")) {
            converted = true
            convert()
        }
    }

    val connection = intercept<StatefulRedisConnection<String, String>>(delegate, replacement = { method ->
        commands.takeIf { method == "reactive" }
    })
}

private inline fun <reified T> intercept(
    delegate: T,
    crossinline replacement: (String) -> Any? = { null },
    crossinline before: (String) -> Unit = {},
): T = T::class.java.cast(Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
    before(method.name)
    replacement(method.name) ?: try {
        method.invoke(delegate, *(args ?: emptyArray()))
    } catch (failure: InvocationTargetException) {
        throw failure.targetException
    }
})
