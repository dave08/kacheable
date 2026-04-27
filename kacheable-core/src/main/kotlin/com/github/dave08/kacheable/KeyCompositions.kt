@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
data class SecondaryKeyComposition2<P1 : Any, P2 : Any>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
) {
    fun encode(p1: P1, p2: P2): CacheArgs = joinArgs(first.encode(p1), second.encode(p2))
    val wildcardArgs: CacheArgs = joinArgs(first.wildcardArgs, second.wildcardArgs)
}

@ExperimentalKacheableApi
data class SecondaryKeyComposition3<P1 : Any, P2 : Any, P3 : Any>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
) {
    fun encode(p1: P1, p2: P2, p3: P3): CacheArgs = joinArgs(first.encode(p1), second.encode(p2), third.encode(p3))
    val wildcardArgs: CacheArgs = joinArgs(first.wildcardArgs, second.wildcardArgs, third.wildcardArgs)
}

@ExperimentalKacheableApi
data class SecondaryKeyComposition4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
    val fourth: KeyPart<P4>,
) {
    fun encode(p1: P1, p2: P2, p3: P3, p4: P4): CacheArgs =
        joinArgs(first.encode(p1), second.encode(p2), third.encode(p3), fourth.encode(p4))

    val wildcardArgs: CacheArgs =
        joinArgs(first.wildcardArgs, second.wildcardArgs, third.wildcardArgs, fourth.wildcardArgs)
}

@ExperimentalKacheableApi
data class SecondaryKeyComposition5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val first: KeyPart<P1>,
    val second: KeyPart<P2>,
    val third: KeyPart<P3>,
    val fourth: KeyPart<P4>,
    val fifth: KeyPart<P5>,
) {
    fun encode(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): CacheArgs =
        joinArgs(first.encode(p1), second.encode(p2), third.encode(p3), fourth.encode(p4), fifth.encode(p5))

    val wildcardArgs: CacheArgs =
        joinArgs(first.wildcardArgs, second.wildcardArgs, third.wildcardArgs, fourth.wildcardArgs, fifth.wildcardArgs)
}

@ExperimentalKacheableApi
data class MainSecondaryKey2<P1 : Any, P2 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: KeyPart<P2>,
)

@ExperimentalKacheableApi
data class MainSecondaryKey3<P1 : Any, P2 : Any, P3 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: SecondaryKeyComposition2<P2, P3>,
)

@ExperimentalKacheableApi
data class MainSecondaryKey4<P1 : Any, P2 : Any, P3 : Any, P4 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: SecondaryKeyComposition3<P2, P3, P4>,
)

@ExperimentalKacheableApi
data class MainSecondaryKey5<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: SecondaryKeyComposition4<P2, P3, P4, P5>,
)

@ExperimentalKacheableApi
data class MainSecondaryKey6<P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any>(
    val main: MainKeyPart<P1>,
    val secondary: SecondaryKeyComposition5<P2, P3, P4, P5, P6>,
)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> MainKeyPart<P1>.plus(
    secondary: KeyPart<P2>,
): MainSecondaryKey2<P1, P2> = MainSecondaryKey2(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> KeyPart<P1>.plus(
    other: KeyPart<P2>,
): SecondaryKeyComposition2<P1, P2> = SecondaryKeyComposition2(this, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> SecondaryKeyComposition2<P1, P2>.plus(
    other: KeyPart<P3>,
): SecondaryKeyComposition3<P1, P2, P3> = SecondaryKeyComposition3(first, second, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition2<P2, P3>,
): MainSecondaryKey3<P1, P2, P3> = MainSecondaryKey3(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> SecondaryKeyComposition3<P1, P2, P3>.plus(
    other: KeyPart<P4>,
): SecondaryKeyComposition4<P1, P2, P3, P4> = SecondaryKeyComposition4(first, second, third, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition3<P2, P3, P4>,
): MainSecondaryKey4<P1, P2, P3, P4> = MainSecondaryKey4(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> SecondaryKeyComposition4<P1, P2, P3, P4>.plus(
    other: KeyPart<P5>,
): SecondaryKeyComposition5<P1, P2, P3, P4, P5> = SecondaryKeyComposition5(first, second, third, fourth, other)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition4<P2, P3, P4, P5>,
): MainSecondaryKey5<P1, P2, P3, P4, P5> = MainSecondaryKey5(this, secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> SecondaryKeyComposition5<P1, P2, P3, P4, P5>.plus(
    other: KeyPart<P6>,
): Nothing = throw UnsupportedOperationException("Secondary key composition already supports up to 5 parameters")

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> MainKeyPart<P1>.plus(
    secondary: SecondaryKeyComposition5<P2, P3, P4, P5, P6>,
): MainSecondaryKey6<P1, P2, P3, P4, P5, P6> = MainSecondaryKey6(this, secondary)
