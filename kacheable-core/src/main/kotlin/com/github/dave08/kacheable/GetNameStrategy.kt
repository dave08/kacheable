package com.github.dave08.kacheable

fun interface GetNameStrategy {
    fun getName(name: String, params: Array<out Any>): String
}

val DefaultGetNameStrategy: GetNameStrategy = GetNameStrategy { name, params ->
    if (params.isEmpty())
        name
    else
        "$name:${params.joinToString(",")}"
}