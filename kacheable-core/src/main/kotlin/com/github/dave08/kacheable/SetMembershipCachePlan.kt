package com.github.dave08.kacheable

@ExperimentalKacheableApi
internal data class SetMembershipAddress(
    val membersKey: String,
    val nonMembersKey: String,
    val member: String?,
) {
    val requiredMember: String
        get() = requireNotNull(member) { "Set membership cache entries require a secondary key member." }

    fun keyFor(result: Boolean): String = if (result) membersKey else nonMembersKey
}

@ExperimentalKacheableApi
internal data class SetMembershipInvalidationPlan(
    val keys: List<String> = emptyList(),
    val members: List<Pair<String, String>> = emptyList(),
)

@ExperimentalKacheableApi
internal fun setMembershipAddress(
    name: String,
    keyGroups: CacheKeyGroups,
    getNameStrategy: GetNameStrategy,
): SetMembershipAddress {
    val membersKey = getNameStrategy.getName(name, keyGroups.main.toParamsArray())
    return SetMembershipAddress(
        membersKey = membersKey,
        nonMembersKey = "$membersKey:__kacheable_non_members",
        member = keyGroups.secondary?.toParamsArray()?.joinToString(","),
    )
}

@ExperimentalKacheableApi
internal fun SetMembershipAddress.invalidationPlan(): SetMembershipInvalidationPlan =
    if (member == null)
        SetMembershipInvalidationPlan(keys = listOf(membersKey, nonMembersKey))
    else
        SetMembershipInvalidationPlan(
            members = listOf(
                membersKey to member,
                nonMembersKey to member,
            ),
        )

@ExperimentalKacheableApi
internal fun shouldWriteSetMembershipResult(
    result: Boolean,
    cacheFalse: Boolean,
    saveResultIf: (Boolean) -> Boolean,
): Boolean = saveResultIf(result) && (result || cacheFalse)
