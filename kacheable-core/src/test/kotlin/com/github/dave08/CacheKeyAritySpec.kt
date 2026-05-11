@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.returns
import de.infix.testBalloon.framework.core.testSuite

val CacheKeyAritySpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("exact cache keys support arities zero through six") {
            val p1 = keyPart<Int>("p1")
            val p2 = keyPart<Int>("p2")
            val p3 = keyPart<Int>("p3")
            val p4 = keyPart<Int>("p4")
            val p5 = keyPart<Int>("p5")
            val p6 = keyPart<Int>("p6")

            val a0 = cacheKey("arity-exact-0", returns<CachedSong>(), key = exact())
            val a1 = cacheKey("arity-exact-1", returns<CachedSong>(), key = exact(p1))
            val a2 = cacheKey("arity-exact-2", returns<CachedSong>(), key = exact(p1 + p2))
            val a3 = cacheKey("arity-exact-3", returns<CachedSong>(), key = exact(p1 + p2 + p3))
            val a4 = cacheKey("arity-exact-4", returns<CachedSong>(), key = exact(p1 + p2 + p3 + p4))
            val a5 = cacheKey("arity-exact-5", returns<CachedSong>(), key = exact(p1 + p2 + p3 + p4 + p5))
            val a6 = cacheKey("arity-exact-6", returns<CachedSong>(), key = exact(p1 + p2 + p3 + p4 + p5 + p6))

            cache(a0()) { CachedSong(0, "zero") }
            cache(a1(1)) { CachedSong(1, "one") }
            cache(a2(1, 2)) { CachedSong(2, "two") }
            cache(a3(1, 2, 3)) { CachedSong(3, "three") }
            cache(a4(1, 2, 3, 4)) { CachedSong(4, "four") }
            cache(a5(1, 2, 3, 4, 5)) { CachedSong(5, "five") }
            cache(a6(1, 2, 3, 4, 5, 6)) { CachedSong(6, "six") }

            store.assertStringValue("arity-exact-0", """{"id":0,"title":"zero"}""")
            store.assertStringValue("arity-exact-1:1", """{"id":1,"title":"one"}""")
            store.assertStringValue("arity-exact-2:1,2", """{"id":2,"title":"two"}""")
            store.assertStringValue("arity-exact-3:1,2,3", """{"id":3,"title":"three"}""")
            store.assertStringValue("arity-exact-4:1,2,3,4", """{"id":4,"title":"four"}""")
            store.assertStringValue("arity-exact-5:1,2,3,4,5", """{"id":5,"title":"five"}""")
            store.assertStringValue("arity-exact-6:1,2,3,4,5,6", """{"id":6,"title":"six"}""")
        }

        test("single-partition cache keys support item-key arities one through six") {
            val k1 = keyPart<Int>("k1")
            val k2 = keyPart<Int>("k2")
            val k3 = keyPart<Int>("k3")
            val k4 = keyPart<Int>("k4")
            val k5 = keyPart<Int>("k5")
            val k6 = keyPart<Int>("k6")

            val a1 = cacheKey("arity-single-1", returns<CachedSong>(), key = partitioned(key = k1))
            val a2 = cacheKey("arity-single-2", returns<CachedSong>(), key = partitioned(key = k1 + k2))
            val a3 = cacheKey("arity-single-3", returns<CachedSong>(), key = partitioned(key = k1 + k2 + k3))
            val a4 = cacheKey("arity-single-4", returns<CachedSong>(), key = partitioned(key = k1 + k2 + k3 + k4))
            val a5 = cacheKey("arity-single-5", returns<CachedSong>(), key = partitioned(key = k1 + k2 + k3 + k4 + k5))
            val a6 = cacheKey("arity-single-6", returns<CachedSong>(), key = partitioned(key = k1 + k2 + k3 + k4 + k5 + k6))

            cache(a1(1)) { CachedSong(1, "one") }
            cache(a2(1, 2)) { CachedSong(2, "two") }
            cache(a3(1, 2, 3)) { CachedSong(3, "three") }
            cache(a4(1, 2, 3, 4)) { CachedSong(4, "four") }
            cache(a5(1, 2, 3, 4, 5)) { CachedSong(5, "five") }
            cache(a6(1, 2, 3, 4, 5, 6)) { CachedSong(6, "six") }

            store.assertHashField("arity-single-1", "1", """{"id":1,"title":"one"}""")
            store.assertHashField("arity-single-2", "1,2", """{"id":2,"title":"two"}""")
            store.assertHashField("arity-single-3", "1,2,3", """{"id":3,"title":"three"}""")
            store.assertHashField("arity-single-4", "1,2,3,4", """{"id":4,"title":"four"}""")
            store.assertHashField("arity-single-5", "1,2,3,4,5", """{"id":5,"title":"five"}""")
            store.assertHashField("arity-single-6", "1,2,3,4,5,6", """{"id":6,"title":"six"}""")
        }

        test("partitioned cache keys support every partition and item-key split up to total arity six") {
            val p1 = keyPart<Int>("p1")
            val p2 = keyPart<Int>("p2")
            val p3 = keyPart<Int>("p3")
            val p4 = keyPart<Int>("p4")
            val p5 = keyPart<Int>("p5")
            val k1 = keyPart<Int>("k1")
            val k2 = keyPart<Int>("k2")
            val k3 = keyPart<Int>("k3")
            val k4 = keyPart<Int>("k4")
            val k5 = keyPart<Int>("k5")

            val p1x1 = cacheKey("arity-1x1", returns<CachedSong>(), key = partitioned(partition = p1, key = k1))
            val p1x2 = cacheKey("arity-1x2", returns<CachedSong>(), key = partitioned(partition = p1, key = k1 + k2))
            val p1x3 = cacheKey("arity-1x3", returns<CachedSong>(), key = partitioned(partition = p1, key = k1 + k2 + k3))
            val p1x4 = cacheKey("arity-1x4", returns<CachedSong>(), key = partitioned(partition = p1, key = k1 + k2 + k3 + k4))
            val p1x5 = cacheKey("arity-1x5", returns<CachedSong>(), key = partitioned(partition = p1, key = k1 + k2 + k3 + k4 + k5))
            val p2x1 = cacheKey("arity-2x1", returns<CachedSong>(), key = partitioned(partition = p1 + p2, key = k1))
            val p2x2 = cacheKey("arity-2x2", returns<CachedSong>(), key = partitioned(partition = p1 + p2, key = k1 + k2))
            val p2x3 = cacheKey("arity-2x3", returns<CachedSong>(), key = partitioned(partition = p1 + p2, key = k1 + k2 + k3))
            val p2x4 = cacheKey("arity-2x4", returns<CachedSong>(), key = partitioned(partition = p1 + p2, key = k1 + k2 + k3 + k4))
            val p3x1 = cacheKey("arity-3x1", returns<CachedSong>(), key = partitioned(partition = p1 + p2 + p3, key = k1))
            val p3x2 = cacheKey("arity-3x2", returns<CachedSong>(), key = partitioned(partition = p1 + p2 + p3, key = k1 + k2))
            val p3x3 = cacheKey("arity-3x3", returns<CachedSong>(), key = partitioned(partition = p1 + p2 + p3, key = k1 + k2 + k3))
            val p4x1 = cacheKey("arity-4x1", returns<CachedSong>(), key = partitioned(partition = p1 + p2 + p3 + p4, key = k1))
            val p4x2 = cacheKey("arity-4x2", returns<CachedSong>(), key = partitioned(partition = p1 + p2 + p3 + p4, key = k1 + k2))
            val p5x1 = cacheKey("arity-5x1", returns<CachedSong>(), key = partitioned(partition = p1 + p2 + p3 + p4 + p5, key = k1))

            cache(p1x1(1, 1)) { CachedSong(11, "1x1") }
            cache(p1x2(1, 1, 2)) { CachedSong(12, "1x2") }
            cache(p1x3(1, 1, 2, 3)) { CachedSong(13, "1x3") }
            cache(p1x4(1, 1, 2, 3, 4)) { CachedSong(14, "1x4") }
            cache(p1x5(1, 1, 2, 3, 4, 5)) { CachedSong(15, "1x5") }
            cache(p2x1(1, 2, 1)) { CachedSong(21, "2x1") }
            cache(p2x2(1, 2, 1, 2)) { CachedSong(22, "2x2") }
            cache(p2x3(1, 2, 1, 2, 3)) { CachedSong(23, "2x3") }
            cache(p2x4(1, 2, 1, 2, 3, 4)) { CachedSong(24, "2x4") }
            cache(p3x1(1, 2, 3, 1)) { CachedSong(31, "3x1") }
            cache(p3x2(1, 2, 3, 1, 2)) { CachedSong(32, "3x2") }
            cache(p3x3(1, 2, 3, 1, 2, 3)) { CachedSong(33, "3x3") }
            cache(p4x1(1, 2, 3, 4, 1)) { CachedSong(41, "4x1") }
            cache(p4x2(1, 2, 3, 4, 1, 2)) { CachedSong(42, "4x2") }
            cache(p5x1(1, 2, 3, 4, 5, 1)) { CachedSong(51, "5x1") }

            store.assertHashField("arity-1x1:1", "1", """{"id":11,"title":"1x1"}""")
            store.assertHashField("arity-1x2:1", "1,2", """{"id":12,"title":"1x2"}""")
            store.assertHashField("arity-1x3:1", "1,2,3", """{"id":13,"title":"1x3"}""")
            store.assertHashField("arity-1x4:1", "1,2,3,4", """{"id":14,"title":"1x4"}""")
            store.assertHashField("arity-1x5:1", "1,2,3,4,5", """{"id":15,"title":"1x5"}""")
            store.assertHashField("arity-2x1:1,2", "1", """{"id":21,"title":"2x1"}""")
            store.assertHashField("arity-2x2:1,2", "1,2", """{"id":22,"title":"2x2"}""")
            store.assertHashField("arity-2x3:1,2", "1,2,3", """{"id":23,"title":"2x3"}""")
            store.assertHashField("arity-2x4:1,2", "1,2,3,4", """{"id":24,"title":"2x4"}""")
            store.assertHashField("arity-3x1:1,2,3", "1", """{"id":31,"title":"3x1"}""")
            store.assertHashField("arity-3x2:1,2,3", "1,2", """{"id":32,"title":"3x2"}""")
            store.assertHashField("arity-3x3:1,2,3", "1,2,3", """{"id":33,"title":"3x3"}""")
            store.assertHashField("arity-4x1:1,2,3,4", "1", """{"id":41,"title":"4x1"}""")
            store.assertHashField("arity-4x2:1,2,3,4", "1,2", """{"id":42,"title":"4x2"}""")
            store.assertHashField("arity-5x1:1,2,3,4,5", "1", """{"id":51,"title":"5x1"}""")
        }
    }
}
