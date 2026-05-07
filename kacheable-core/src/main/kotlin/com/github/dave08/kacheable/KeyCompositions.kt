@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.keys.TypedPrimaryKeyDefinition
import com.github.dave08.kacheable.internal.keys.TypedPrimarySecondaryKeyDefinition
import com.github.dave08.kacheable.internal.keys.validateUniqueKeyPartNames

@ExperimentalKacheableApi
data class KeyPartComposition2<P1, P2>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
) : TypedPrimaryKeyDefinition {
    init {
        validateUniqueKeyPartNames(partNames())
    }

    fun encode(p1: P1, p2: P2): CacheArgs = joinArgs(first.encode(p1), second.encode(p2))
    internal fun encodeParts(p1: P1, p2: P2): List<CacheArgs> = listOf(first.encodePart(p1), second.encodePart(p2))
    override fun partNames(): List<String?> = listOf(first.name, second.name)
}

@ExperimentalKacheableApi
data class KeyPartComposition3<P1, P2, P3>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
) : TypedPrimaryKeyDefinition {
    init {
        validateUniqueKeyPartNames(partNames())
    }

    fun encode(p1: P1, p2: P2, p3: P3): CacheArgs = joinArgs(first.encode(p1), second.encode(p2), third.encode(p3))
    internal fun encodeParts(p1: P1, p2: P2, p3: P3): List<CacheArgs> =
        listOf(first.encodePart(p1), second.encodePart(p2), third.encodePart(p3))
    override fun partNames(): List<String?> = listOf(first.name, second.name, third.name)
}

@ExperimentalKacheableApi
data class KeyPartComposition4<P1, P2, P3, P4>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
    val fourth: KeyPart<P4>,
) : TypedPrimaryKeyDefinition {
    init {
        validateUniqueKeyPartNames(partNames())
    }

    fun encode(p1: P1, p2: P2, p3: P3, p4: P4): CacheArgs =
        joinArgs(first.encode(p1), second.encode(p2), third.encode(p3), fourth.encode(p4))
    internal fun encodeParts(p1: P1, p2: P2, p3: P3, p4: P4): List<CacheArgs> =
        listOf(first.encodePart(p1), second.encodePart(p2), third.encodePart(p3), fourth.encodePart(p4))
    override fun partNames(): List<String?> = listOf(first.name, second.name, third.name, fourth.name)
}

@ExperimentalKacheableApi
data class KeyPartComposition5<P1, P2, P3, P4, P5>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
    val fourth: KeyPart<P4>,
    val fifth: KeyPart<P5>,
) : TypedPrimaryKeyDefinition {
    init {
        validateUniqueKeyPartNames(partNames())
    }

    fun encode(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheArgs =
        joinArgs(first.encode(p1), second.encode(p2), third.encode(p3), fourth.encode(p4), fifth.encode(p5))
    internal fun encodeParts(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): List<CacheArgs> =
        listOf(first.encodePart(p1), second.encodePart(p2), third.encodePart(p3), fourth.encodePart(p4), fifth.encodePart(p5))
    override fun partNames(): List<String?> = listOf(first.name, second.name, third.name, fourth.name, fifth.name)
}

@ExperimentalKacheableApi
data class KeyPartComposition6<P1, P2, P3, P4, P5, P6>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
    val fourth: KeyPart<P4>,
    val fifth: KeyPart<P5>,
    val sixth: KeyPart<P6>,
) : TypedPrimaryKeyDefinition {
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
    override fun partNames(): List<String?> = listOf(first.name, second.name, third.name, fourth.name, fifth.name, sixth.name)
}

@ExperimentalKacheableApi
data class KeyPartPrimarySecondary2<P1 : Any, P2 : Any>(
    override val primary: KeyPart<P1>,
    val secondary: KeyPart<P2>,
) : TypedPrimarySecondaryKeyDefinition<P1> {
    init {
        validateUniqueKeyPartNames(primaryPartNames() + secondaryPartNames())
    }

    override fun encodePrimaryParts(p1: P1): List<CacheArgs> = listOf(primary.encodePart(p1))
    override fun primaryPartNames(): List<String?> = listOf(primary.name)
    internal fun encodeSecondaryParts(p2: P2): List<CacheArgs> = listOf(secondary.encodePart(p2))
    override fun secondaryPartNames(): List<String?> = listOf(secondary.name)
    override fun secondaryParts(): List<KeyPart<*>> = listOf(secondary)
}

@ExperimentalKacheableApi
data class KeyPartPrimarySecondary3<P1 : Any, P2 : Any, P3 : Any>(
    override val primary: KeyPart<P1>,
    val secondary: KeyPartComposition2<P2, P3>,
) : TypedPrimarySecondaryKeyDefinition<P1> {
    init {
        validateUniqueKeyPartNames(primaryPartNames() + secondaryPartNames())
    }

    override fun encodePrimaryParts(p1: P1): List<CacheArgs> = listOf(primary.encodePart(p1))
    override fun primaryPartNames(): List<String?> = listOf(primary.name)
    internal fun encodeSecondaryParts(p2: P2, p3: P3): List<CacheArgs> = secondary.encodeParts(p2, p3)
    override fun secondaryPartNames(): List<String?> = secondary.partNames()
    override fun secondaryParts(): List<KeyPart<*>> = listOf(secondary.first, secondary.second)
}

@ExperimentalKacheableApi
data class KeyPartPrimarySecondary4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    override val primary: KeyPart<P1>,
    val secondary: KeyPartComposition3<P2, P3, P4>,
) : TypedPrimarySecondaryKeyDefinition<P1> {
    init {
        validateUniqueKeyPartNames(primaryPartNames() + secondaryPartNames())
    }

    override fun encodePrimaryParts(p1: P1): List<CacheArgs> = listOf(primary.encodePart(p1))
    override fun primaryPartNames(): List<String?> = listOf(primary.name)
    internal fun encodeSecondaryParts(p2: P2, p3: P3, p4: P4): List<CacheArgs> = secondary.encodeParts(p2, p3, p4)
    override fun secondaryPartNames(): List<String?> = secondary.partNames()
    override fun secondaryParts(): List<KeyPart<*>> = listOf(secondary.first, secondary.second, secondary.third)
}

@ExperimentalKacheableApi
data class KeyPartPrimarySecondary5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    override val primary: KeyPart<P1>,
    val secondary: KeyPartComposition4<P2, P3, P4, P5>,
) : TypedPrimarySecondaryKeyDefinition<P1> {
    init {
        validateUniqueKeyPartNames(primaryPartNames() + secondaryPartNames())
    }

    override fun encodePrimaryParts(p1: P1): List<CacheArgs> = listOf(primary.encodePart(p1))
    override fun primaryPartNames(): List<String?> = listOf(primary.name)
    internal fun encodeSecondaryParts(p2: P2, p3: P3, p4: P4, p5: P5): List<CacheArgs> = secondary.encodeParts(p2, p3, p4, p5)
    override fun secondaryPartNames(): List<String?> = secondary.partNames()
    override fun secondaryParts(): List<KeyPart<*>> = listOf(secondary.first, secondary.second, secondary.third, secondary.fourth)
}

@ExperimentalKacheableApi
data class KeyPartPrimarySecondary6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    override val primary: KeyPart<P1>,
    val secondary: KeyPartComposition5<P2, P3, P4, P5, P6>,
) : TypedPrimarySecondaryKeyDefinition<P1> {
    init {
        validateUniqueKeyPartNames(primaryPartNames() + secondaryPartNames())
    }

    override fun encodePrimaryParts(p1: P1): List<CacheArgs> = listOf(primary.encodePart(p1))
    override fun primaryPartNames(): List<String?> = listOf(primary.name)
    internal fun encodeSecondaryParts(p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): List<CacheArgs> =
        secondary.encodeParts(p2, p3, p4, p5, p6)
    override fun secondaryPartNames(): List<String?> = secondary.partNames()
    override fun secondaryParts(): List<KeyPart<*>> =
        listOf(secondary.first, secondary.second, secondary.third, secondary.fourth, secondary.fifth)
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
operator fun <P1 : Any, P2 : Any, P3 : Any> KeyPartPrimarySecondary2<P1, P2>.plus(
    other: KeyPart<P3>,
): KeyPartPrimarySecondary3<P1, P2, P3> = KeyPartPrimarySecondary3(primary, secondary + other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> KeyPart<P1>.times(
    secondary: KeyPartComposition2<P2, P3>,
): KeyPartPrimarySecondary3<P1, P2, P3> = KeyPartPrimarySecondary3(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> KeyPart<P1>.times(
    secondary: KeyPart<P2>,
): KeyPartPrimarySecondary2<P1, P2> = KeyPartPrimarySecondary2(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> KeyPartPrimarySecondary3<P1, P2, P3>.plus(
    other: KeyPart<P4>,
): KeyPartPrimarySecondary4<P1, P2, P3, P4> = KeyPartPrimarySecondary4(primary, secondary + other)

@ExperimentalKacheableApi
operator fun <P1, P2, P3, P4, P5> KeyPartComposition4<P1, P2, P3, P4>.plus(
    other: KeyPart<P5>,
): KeyPartComposition5<P1, P2, P3, P4, P5> = KeyPartComposition5(first, second, third, fourth, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> KeyPart<P1>.times(
    secondary: KeyPartComposition3<P2, P3, P4>,
): KeyPartPrimarySecondary4<P1, P2, P3, P4> = KeyPartPrimarySecondary4(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> KeyPartPrimarySecondary4<P1, P2, P3, P4>.plus(
    other: KeyPart<P5>,
): KeyPartPrimarySecondary5<P1, P2, P3, P4, P5> = KeyPartPrimarySecondary5(primary, secondary + other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> KeyPart<P1>.times(
    secondary: KeyPartComposition4<P2, P3, P4, P5>,
): KeyPartPrimarySecondary5<P1, P2, P3, P4, P5> = KeyPartPrimarySecondary5(this, secondary)

@ExperimentalKacheableApi
operator fun <P1, P2, P3, P4, P5, P6> KeyPartComposition5<P1, P2, P3, P4, P5>.plus(
    other: KeyPart<P6>,
): KeyPartComposition6<P1, P2, P3, P4, P5, P6> =
    KeyPartComposition6(first, second, third, fourth, fifth, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> KeyPartPrimarySecondary5<P1, P2, P3, P4, P5>.plus(
    other: KeyPart<P6>,
): KeyPartPrimarySecondary6<P1, P2, P3, P4, P5, P6> = KeyPartPrimarySecondary6(primary, secondary + other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> KeyPart<P1>.times(
    secondary: KeyPartComposition5<P2, P3, P4, P5, P6>,
): KeyPartPrimarySecondary6<P1, P2, P3, P4, P5, P6> = KeyPartPrimarySecondary6(this, secondary)
