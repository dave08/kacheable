@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.keys.validateUniqueKeyPartNames

@ExperimentalKacheableApi
data class KeyPartComposition2<P1, P2>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
) {
    init {
        validateUniqueKeyPartNames(partNames())
    }

    fun encode(p1: P1, p2: P2): CacheArgs = joinArgs(first.encode(p1), second.encode(p2))
    internal fun encodeParts(p1: P1, p2: P2): List<CacheArgs> = listOf(first.encodePart(p1), second.encodePart(p2))
    internal fun partNames(): List<String?> = listOf(first.name, second.name)
}

@ExperimentalKacheableApi
data class KeyPartComposition3<P1, P2, P3>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
) {
    init {
        validateUniqueKeyPartNames(partNames())
    }

    fun encode(p1: P1, p2: P2, p3: P3): CacheArgs = joinArgs(first.encode(p1), second.encode(p2), third.encode(p3))
    internal fun encodeParts(p1: P1, p2: P2, p3: P3): List<CacheArgs> =
        listOf(first.encodePart(p1), second.encodePart(p2), third.encodePart(p3))
    internal fun partNames(): List<String?> = listOf(first.name, second.name, third.name)
}

@ExperimentalKacheableApi
data class KeyPartComposition4<P1, P2, P3, P4>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
    val fourth: KeyPart<P4>,
) {
    init {
        validateUniqueKeyPartNames(partNames())
    }

    fun encode(p1: P1, p2: P2, p3: P3, p4: P4): CacheArgs =
        joinArgs(first.encode(p1), second.encode(p2), third.encode(p3), fourth.encode(p4))
    internal fun encodeParts(p1: P1, p2: P2, p3: P3, p4: P4): List<CacheArgs> =
        listOf(first.encodePart(p1), second.encodePart(p2), third.encodePart(p3), fourth.encodePart(p4))
    internal fun partNames(): List<String?> = listOf(first.name, second.name, third.name, fourth.name)
}

@ExperimentalKacheableApi
data class KeyPartComposition5<P1, P2, P3, P4, P5>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
    val fourth: KeyPart<P4>,
    val fifth: KeyPart<P5>,
) {
    init {
        validateUniqueKeyPartNames(partNames())
    }

    fun encode(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheArgs =
        joinArgs(first.encode(p1), second.encode(p2), third.encode(p3), fourth.encode(p4), fifth.encode(p5))
    internal fun encodeParts(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): List<CacheArgs> =
        listOf(first.encodePart(p1), second.encodePart(p2), third.encodePart(p3), fourth.encodePart(p4), fifth.encodePart(p5))
    internal fun partNames(): List<String?> = listOf(first.name, second.name, third.name, fourth.name, fifth.name)
}

@ExperimentalKacheableApi
data class KeyPartComposition6<P1, P2, P3, P4, P5, P6>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
    val fourth: KeyPart<P4>,
    val fifth: KeyPart<P5>,
    val sixth: KeyPart<P6>,
) {
    init {
        validateUniqueKeyPartNames(partNames())
    }

    fun encode(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): CacheArgs =
        joinArgs(
            first.encode(p1),
            second.encode(p2),
            third.encode(p3),
            fourth.encode(p4),
            fifth.encode(p5),
            sixth.encode(p6),
        )
    internal fun encodeParts(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): List<CacheArgs> =
        listOf(
            first.encodePart(p1),
            second.encodePart(p2),
            third.encodePart(p3),
            fourth.encodePart(p4),
            fifth.encodePart(p5),
            sixth.encodePart(p6),
        )
    internal fun partNames(): List<String?> = listOf(first.name, second.name, third.name, fourth.name, fifth.name, sixth.name)
}

@ExperimentalKacheableApi
operator fun <P1, P2> KeyPart<P1>.plus(
    other: KeyPart<P2>,
): KeyPartComposition2<P1, P2> = KeyPartComposition2(this, other)

@ExperimentalKacheableApi
operator fun <P1, P2, P3> KeyPartComposition2<P1, P2>.plus(
    other: KeyPart<P3>,
): KeyPartComposition3<P1, P2, P3> = KeyPartComposition3(first, second, other)

@ExperimentalKacheableApi
operator fun <P1, P2, P3, P4> KeyPartComposition3<P1, P2, P3>.plus(
    other: KeyPart<P4>,
): KeyPartComposition4<P1, P2, P3, P4> = KeyPartComposition4(first, second, third, other)

@ExperimentalKacheableApi
operator fun <P1, P2, P3, P4, P5> KeyPartComposition4<P1, P2, P3, P4>.plus(
    other: KeyPart<P5>,
): KeyPartComposition5<P1, P2, P3, P4, P5> = KeyPartComposition5(first, second, third, fourth, other)

@ExperimentalKacheableApi
operator fun <P1, P2, P3, P4, P5, P6> KeyPartComposition5<P1, P2, P3, P4, P5>.plus(
    other: KeyPart<P6>,
): KeyPartComposition6<P1, P2, P3, P4, P5, P6> =
    KeyPartComposition6(first, second, third, fourth, fifth, other)
