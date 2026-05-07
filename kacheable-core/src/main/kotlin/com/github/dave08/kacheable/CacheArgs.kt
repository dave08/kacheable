@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
sealed interface CacheArgs {
    fun toParamsArray(): Array<out Any?>
}

@ExperimentalKacheableApi
data object CacheArgs0 : CacheArgs {
    override fun toParamsArray(): Array<out Any?> = emptyArray()
}

@ExperimentalKacheableApi
class CachePatternArgs(
    private vararg val params: Any?,
) : CacheArgs {
    override fun toParamsArray(): Array<out Any?> = params
}

@ExperimentalKacheableApi
fun argsOf(vararg params: Any?): CacheArgs = CachePatternArgs(*params)

@ExperimentalKacheableApi
fun patternArgs(vararg params: Any?): CacheArgs = CachePatternArgs(*params)

@PublishedApi
internal fun joinArgs(vararg segments: CacheArgs): CacheArgs {
    val params = segments.flatMap { it.toParamsArray().toList() }
    return CachePatternArgs(*params.toTypedArray())
}
