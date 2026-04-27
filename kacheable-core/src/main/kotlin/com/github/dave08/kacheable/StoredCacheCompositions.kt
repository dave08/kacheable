@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> HashMapMainKey<P1>.plus(
    secondary: KeyPart<P2>,
): HashMapStoredCache2<P1, P2> = HashMapStoredCache2(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> SetMainKey<P1>.plus(
    member: KeyPart<P2>,
): SetStoredCache2<P1, P2> = SetStoredCache2(name, key + member)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition2<P2, P3>,
): HashMapStoredCache3<P1, P2, P3> = HashMapStoredCache3(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition3<P2, P3, P4>,
): HashMapStoredCache4<P1, P2, P3, P4> = HashMapStoredCache4(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition4<P2, P3, P4, P5>,
): HashMapStoredCache5<P1, P2, P3, P4, P5> = HashMapStoredCache5(name, key + secondary)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> HashMapMainKey<P1>.plus(
    secondary: SecondaryKeyComposition5<P2, P3, P4, P5, P6>,
): HashMapStoredCache6<P1, P2, P3, P4, P5, P6> = HashMapStoredCache6(name, key + secondary)
