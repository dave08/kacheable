package com.github.dave08

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import com.github.dave08.kacheable.store.KacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.test.assertEquals

val SetRefreshRecheckSpec by testSuite {
    testFixture { SetRefreshRaceFixture() } asContextForEach {
        test("set membership refresh loader receives the latest value from the coordination recheck") {
            var loaderPrevious: Boolean? = null
            arrangeMembership(false)

            val result = runMembershipRefresh {
                loaderPrevious = it
                false
            }

            assertEquals(false, result)
            assertEquals(true, loaderPrevious)
        }

        test("failed set membership refresh falls back to the latest value from the coordination recheck") {
            arrangeMembership(false)

            val result = runMembershipRefresh {
                error("refresh failed")
            }

            assertEquals(true, result)
        }

        test("enum classification refresh loader receives the latest value from the coordination recheck") {
            var loaderPrevious: SongLike? = null
            arrangeClassification(SongLike.LIKE)

            val result = runClassificationRefresh {
                loaderPrevious = it
                SongLike.NONE
            }

            assertEquals(SongLike.NONE, result)
            assertEquals(SongLike.DISLIKE, loaderPrevious)
        }

        test("failed enum classification refresh falls back to the latest value from the coordination recheck") {
            arrangeClassification(SongLike.LIKE)

            val result = runClassificationRefresh {
                error("refresh failed")
            }

            assertEquals(SongLike.DISLIKE, result)
        }
    }
}

private class SetRefreshRaceFixture {
    private val store = SetRefreshRaceStore()
    private val competingLoadBlocked = CompletableDeferred<Unit>()
    private val releaseCompetingLoad = CompletableDeferred<Unit>()
    private val owner = 7
    private val targetAccount = 41
    private val blockerAccount = 42
    private val membership = cacheKey(
        MEMBERSHIP_CACHE,
        returns<Boolean>(),
        key = partitioned(keyPart<Int>("owner"), keyPart<Int>("account")),
    )
    private val classification = cacheKey(
        CLASSIFICATION_CACHE,
        returns<SongLike>(),
        key = partitioned(keyPart<Int>("owner"), keyPart<Int>("account")),
    )
    private val cache = Kacheable(
        store = store,
        configs = listOf(MEMBERSHIP_CACHE, CLASSIFICATION_CACHE).associateWith { name ->
            CacheConfig(
                name,
                resilience = CacheResilienceConfig(
                    singleFlight = SingleFlightMode.Local,
                    maxConcurrentLoads = 1,
                ),
            )
        },
    )

    fun arrangeMembership(value: Boolean) {
        store.arrangeMembership(
            initialKey = membershipKey(value),
            latestKey = membershipKey(!value),
            member = targetAccount.toString(),
        )
    }

    fun arrangeClassification(value: SongLike) {
        store.arrangeMembership(
            initialKey = classificationKey(value),
            latestKey = classificationKey(SongLike.DISLIKE),
            member = targetAccount.toString(),
        )
    }

    suspend fun runMembershipRefresh(loader: suspend (Boolean?) -> Boolean): Boolean = coroutineScope {
        val blocker = async(start = CoroutineStart.UNDISPATCHED) {
            cache.cache(membership(owner, blockerAccount)) { holdCompetingLoad(); true }
        }
        awaitCompetingLoadBlocked()
        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            cache.cache(
                membership(owner, targetAccount),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = CacheRefreshPolicy.refreshIf { true },
                storeResultIf = { true },
                block = loader,
            )
        }

        store.awaitInitialTargetRead()
        store.publishLatestMembership()
        releaseCompetingLoad.complete(Unit)

        refresh.await().also { blocker.await() }
    }

    suspend fun runClassificationRefresh(loader: suspend (SongLike?) -> SongLike): SongLike = coroutineScope {
        val blocker = async(start = CoroutineStart.UNDISPATCHED) {
            cache.cache(classification(owner, blockerAccount)) { holdCompetingLoad(); SongLike.NONE }
        }
        awaitCompetingLoadBlocked()
        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            cache.cache(
                classification(owner, targetAccount),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = CacheRefreshPolicy.refreshIf { true },
                storeResultIf = { true },
                block = loader,
            )
        }

        store.awaitInitialTargetRead()
        store.publishLatestMembership()
        releaseCompetingLoad.complete(Unit)

        refresh.await().also { blocker.await() }
    }

    private suspend fun holdCompetingLoad() {
        competingLoadBlocked.complete(Unit)
        releaseCompetingLoad.await()
    }

    private suspend fun awaitCompetingLoadBlocked() = competingLoadBlocked.await()

    private fun membershipKey(value: Boolean): String =
        if (value) "$MEMBERSHIP_CACHE:$owner" else "$MEMBERSHIP_CACHE:$owner:__kacheable_non_members"

    private fun classificationKey(value: SongLike): String = "$CLASSIFICATION_CACHE:$owner:${value.name}"

    private companion object {
        const val MEMBERSHIP_CACHE = "set-refresh-membership"
        const val CLASSIFICATION_CACHE = "set-refresh-classification"
    }
}

private class SetRefreshRaceStore(
    private val backingStore: InMemoryKacheableStore = InMemoryKacheableStore(),
) : KacheableStore by backingStore {
    private val initialTargetRead = CompletableDeferred<Unit>()
    private lateinit var initialKey: String
    private lateinit var latestKey: String
    private lateinit var targetMember: String

    override suspend fun isSetMember(key: String, member: String): Boolean {
        val present = backingStore.isSetMember(key, member)
        if (key == initialKey && member == targetMember && present) {
            initialTargetRead.complete(Unit)
        }
        return present
    }

    fun arrangeMembership(initialKey: String, latestKey: String, member: String) {
        this.initialKey = initialKey
        this.latestKey = latestKey
        targetMember = member
        backingStore.sets.getOrPut(initialKey, ::mutableSetOf) += member
    }

    suspend fun awaitInitialTargetRead() = initialTargetRead.await()

    fun publishLatestMembership() {
        backingStore.sets[initialKey]?.remove(targetMember)
        backingStore.sets.getOrPut(latestKey, ::mutableSetOf) += targetMember
    }
}
