package com.github.dave08.kacheable.internal.storage

import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.primaryKey
import com.github.dave08.kacheable.requireSecondaryEntry
import com.github.dave08.kacheable.withInternalSuffix

private const val NON_MEMBERS_SUFFIX = "__kacheable_non_members"

internal data class SetMembershipEntry(
    val membersKey: String,
    val nonMembersKey: String,
    val member: String?,
) {
    val requiredMember: String
        get() = requireNotNull(member) { "Set membership cache entries require a secondary key member." }

    fun keyFor(result: Boolean): String = if (result) membersKey else nonMembersKey

    fun classifiedKey(valueName: String): String = "$membersKey:$valueName"
}

internal data class SetMembershipInvalidationPlan(
    val keys: List<String> = emptyList(),
    val members: List<Pair<String, String>> = emptyList(),
)

internal fun setMembershipEntry(
    name: String,
    cacheArgs: PrimarySecondaryCacheArgs,
    namingStrategy: CacheNamingStrategy,
): SetMembershipEntry {
    val entryName = namingStrategy.getEntryName(
        cacheName = name,
        primaryParams = cacheArgs.primary.toParamsArray(),
        secondaryParams = cacheArgs.secondary?.toParamsArray() ?: emptyArray(),
    )
    return SetMembershipEntry(
        membersKey = entryName.primaryKey,
        nonMembersKey = entryName.withInternalSuffix(NON_MEMBERS_SUFFIX),
        member = when {
            cacheArgs.secondary == null -> null
            else -> entryName.requireSecondaryEntry()
        },
    )
}

internal fun SetMembershipEntry.invalidationPlan(): SetMembershipInvalidationPlan =
    if (member == null)
        SetMembershipInvalidationPlan(keys = listOf(membersKey, nonMembersKey))
    else
        SetMembershipInvalidationPlan(
            members = listOf(
                membersKey to member,
                nonMembersKey to member,
            ),
        )

internal fun shouldWriteSetMembershipResult(
    result: Boolean,
    cacheFalse: Boolean,
    saveResultIf: (Boolean) -> Boolean,
): Boolean = saveResultIf(result) && (result || cacheFalse)

internal fun SetMembershipEntry.classificationInvalidationPlan(
    valueNames: List<String>,
): SetMembershipInvalidationPlan =
    if (member == null)
        SetMembershipInvalidationPlan(keys = valueNames.map { classifiedKey(it) })
    else
        SetMembershipInvalidationPlan(members = valueNames.map { classifiedKey(it) to member })

internal fun <R : Any> SetMembershipEntry.keyForClassificationResult(
    result: R,
    values: List<R>,
    valueName: (R) -> String,
): String {
    val resultName = valueName(result)
    require(values.any { valueName(it) == resultName }) {
        "Set classification result must be one of the configured values."
    }
    return classifiedKey(resultName)
}
