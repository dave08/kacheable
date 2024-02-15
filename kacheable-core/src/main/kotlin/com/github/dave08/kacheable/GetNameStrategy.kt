package com.github.dave08.kacheable

fun interface GetNameStrategy {
    fun getName(name: String, params: Array<out Any>): String
}