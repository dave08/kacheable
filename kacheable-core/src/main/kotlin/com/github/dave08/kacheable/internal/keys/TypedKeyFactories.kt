@file:OptIn(com.github.dave08.kacheable.ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.internal.keys

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.KeyPartComposition2
import com.github.dave08.kacheable.KeyPartComposition3
import com.github.dave08.kacheable.KeyPartComposition4
import com.github.dave08.kacheable.KeyPartComposition5
import com.github.dave08.kacheable.KeyPartComposition6
import com.github.dave08.kacheable.KeyPartPrimarySecondary2
import com.github.dave08.kacheable.KeyPartPrimarySecondary3
import com.github.dave08.kacheable.KeyPartPrimarySecondary4
import com.github.dave08.kacheable.KeyPartPrimarySecondary5
import com.github.dave08.kacheable.KeyPartPrimarySecondary6
import com.github.dave08.kacheable.SupportsPrimaryKeyStorage
import com.github.dave08.kacheable.SupportsPrimarySecondaryKeyStorage
import com.github.dave08.kacheable.SupportsValueView
import com.github.dave08.kacheable.TypedPrimaryKey
import com.github.dave08.kacheable.TypedPrimaryKey2
import com.github.dave08.kacheable.TypedPrimaryKey3
import com.github.dave08.kacheable.TypedPrimaryKey4
import com.github.dave08.kacheable.TypedPrimaryKey5
import com.github.dave08.kacheable.TypedPrimaryKey6
import com.github.dave08.kacheable.TypedPrimarySecondaryKey2
import com.github.dave08.kacheable.TypedPrimarySecondaryKey3
import com.github.dave08.kacheable.TypedPrimarySecondaryKey4
import com.github.dave08.kacheable.TypedPrimarySecondaryKey5
import com.github.dave08.kacheable.TypedPrimarySecondaryKey6
import com.github.dave08.kacheable.times

internal fun <P1 : Any, S> typedPrimaryEntryKey(
    name: String,
    key: KeyPart<P1>,
    storedAs: S,
): TypedPrimaryKey<P1, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage =
    TypedPrimaryKey(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, S> typedPrimaryEntryKey(
    name: String,
    key: KeyPartComposition2<P1, P2>,
    storedAs: S,
): TypedPrimaryKey2<P1, P2, S> where S : CacheStorage, S : SupportsValueView =
    TypedPrimaryKey2(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, S> typedPrimaryEntryKey(
    name: String,
    key: KeyPartComposition3<P1, P2, P3>,
    storedAs: S,
): TypedPrimaryKey3<P1, P2, P3, S> where S : CacheStorage, S : SupportsValueView =
    TypedPrimaryKey3(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, S> typedPrimaryEntryKey(
    name: String,
    key: KeyPartComposition4<P1, P2, P3, P4>,
    storedAs: S,
): TypedPrimaryKey4<P1, P2, P3, P4, S> where S : CacheStorage, S : SupportsValueView =
    TypedPrimaryKey4(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S> typedPrimaryEntryKey(
    name: String,
    key: KeyPartComposition5<P1, P2, P3, P4, P5>,
    storedAs: S,
): TypedPrimaryKey5<P1, P2, P3, P4, P5, S> where S : CacheStorage, S : SupportsValueView =
    TypedPrimaryKey5(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S> typedPrimaryEntryKey(
    name: String,
    key: KeyPartComposition6<P1, P2, P3, P4, P5, P6>,
    storedAs: S,
): TypedPrimaryKey6<P1, P2, P3, P4, P5, P6, S> where S : CacheStorage, S : SupportsValueView =
    TypedPrimaryKey6(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, S> typedPrimarySecondaryEntryKey(
    name: String,
    key: KeyPartPrimarySecondary2<P1, P2>,
    storedAs: S,
): TypedPrimarySecondaryKey2<P1, P2, S> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    TypedPrimarySecondaryKey2(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, S> typedPrimarySecondaryEntryKey(
    name: String,
    key: KeyPartPrimarySecondary3<P1, P2, P3>,
    storedAs: S,
): TypedPrimarySecondaryKey3<P1, P2, P3, S> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    TypedPrimarySecondaryKey3(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, S> typedPrimarySecondaryEntryKey(
    name: String,
    key: KeyPartPrimarySecondary4<P1, P2, P3, P4>,
    storedAs: S,
): TypedPrimarySecondaryKey4<P1, P2, P3, P4, S> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    TypedPrimarySecondaryKey4(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S> typedPrimarySecondaryEntryKey(
    name: String,
    key: KeyPartPrimarySecondary5<P1, P2, P3, P4, P5>,
    storedAs: S,
): TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, S> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    TypedPrimarySecondaryKey5(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S> typedPrimarySecondaryEntryKey(
    name: String,
    key: KeyPartPrimarySecondary6<P1, P2, P3, P4, P5, P6>,
    storedAs: S,
): TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, S> where S : CacheStorage, S : SupportsPrimarySecondaryKeyStorage =
    TypedPrimarySecondaryKey6(name, key, storedAs)

internal fun <P1 : Any, P2 : Any, S> appendPrimarySecondaryKey(
    name: String,
    primary: KeyPart<P1>,
    secondary: KeyPart<P2>,
    storedAs: S,
): TypedPrimarySecondaryKey2<P1, P2, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage, S : SupportsPrimarySecondaryKeyStorage =
    typedPrimarySecondaryEntryKey(name, primary * secondary, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, S> appendPrimarySecondaryKey(
    name: String,
    primary: KeyPart<P1>,
    secondary: KeyPartComposition2<P2, P3>,
    storedAs: S,
): TypedPrimarySecondaryKey3<P1, P2, P3, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage, S : SupportsPrimarySecondaryKeyStorage =
    typedPrimarySecondaryEntryKey(name, primary * secondary, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, S> appendPrimarySecondaryKey(
    name: String,
    primary: KeyPart<P1>,
    secondary: KeyPartComposition3<P2, P3, P4>,
    storedAs: S,
): TypedPrimarySecondaryKey4<P1, P2, P3, P4, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage, S : SupportsPrimarySecondaryKeyStorage =
    typedPrimarySecondaryEntryKey(name, primary * secondary, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S> appendPrimarySecondaryKey(
    name: String,
    primary: KeyPart<P1>,
    secondary: KeyPartComposition4<P2, P3, P4, P5>,
    storedAs: S,
): TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage, S : SupportsPrimarySecondaryKeyStorage =
    typedPrimarySecondaryEntryKey(name, primary * secondary, storedAs)

internal fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S> appendPrimarySecondaryKey(
    name: String,
    primary: KeyPart<P1>,
    secondary: KeyPartComposition5<P2, P3, P4, P5, P6>,
    storedAs: S,
): TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage, S : SupportsPrimarySecondaryKeyStorage =
    typedPrimarySecondaryEntryKey(name, primary * secondary, storedAs)
