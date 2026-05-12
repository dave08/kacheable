package com.github.dave08.kacheable

sealed interface CacheArgs {
    fun toParamsArray(): Array<out Any?>
}

data object CacheArgs0 : CacheArgs {
    override fun toParamsArray(): Array<out Any?> = emptyArray()
}

class CachePatternArgs(
    private vararg val params: Any?,
) : CacheArgs {
    override fun toParamsArray(): Array<out Any?> = params
}

fun argsOf(vararg params: Any?): CacheArgs = CachePatternArgs(*params)

fun patternArgs(vararg params: Any?): CacheArgs = CachePatternArgs(*params)

@PublishedApi
internal fun joinArgs(vararg segments: CacheArgs): CacheArgs {
    val params = segments.flatMap { it.toParamsArray().toList() }
    return CachePatternArgs(*params.toTypedArray())
}
