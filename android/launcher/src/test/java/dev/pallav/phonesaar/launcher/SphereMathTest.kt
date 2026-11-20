package dev.pallav.phonesaar.launcher

import dev.pallav.phonesaar.launcher.sphere.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class SphereMathTest {
    @Test fun fibonacciPointsAreUnitAndSpread() {
        val p = SphereMath.fibonacci(60)
        assertEquals(60, p.size)
        for (v in p) assertEquals(1f, sqrt(v.x * v.x + v.y * v.y + v.z * v.z), 1e-4f)
        assertTrue(p.first().y > 0.95f && p.last().y < -0.95f)
        val minDist = p.indices.minOf { i -> p.indices.filter { it != i }.minOf { j -> dist(p[i], p[j]) } }
        assertTrue("points too close: $minDist", minDist > 0.2f)
    }

    private fun dist(a: Vec3, b: Vec3) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))

    @Test fun edgeCounts() {
        assertTrue(SphereMath.fibonacci(0).isEmpty())
        assertEquals(1f, SphereMath.fibonacci(1)[0].z, 0f)
    }

    @Test fun frontFirstPutsRankZeroNearTheViewer() {
        val p = SphereMath.frontFirst(SphereMath.fibonacci(40))
        assertTrue(p[0].z > 0.9f)
        assertTrue(p.zipWithNext().all { it.first.z >= it.second.z })
    }

    @Test fun sizesShrinkWithRank() {
        val sizes = (0 until 30).map { SphereMath.sizeForRank(it, 30, 28f, 64f) }
        assertEquals(64f, sizes[0], 0.01f)
        assertTrue(sizes.zipWithNext().all { it.first >= it.second })
        assertTrue(sizes.last() >= 28f)
    }

    @Test fun rotationPreservesLength() {
        val r = Mat3.aboutY(0.7f) * Mat3.aboutX(-0.4f)
        val v = r.apply(Vec3(0.3f, 0.5f, sqrt(1f - 0.34f)))
        assertEquals(1f, sqrt(v.x * v.x + v.y * v.y + v.z * v.z), 1e-4f)
    }

    @Test fun quarterTurnAboutYMovesFrontToSide() {
        val v = Mat3.aboutY((PI / 2).toFloat()).apply(Vec3(0f, 0f, 1f))
        assertEquals(1f, v.x, 1e-4f); assertEquals(0f, v.z, 1e-4f)
    }

    @Test fun frontIsBiggerAndMoreOpaqueThanBack() {
        val front = SphereMath.project(0, Vec3(0f, 0f, 1f), Mat3.IDENTITY, 100f, 200f, 300f, 2.6f)
        val back = SphereMath.project(1, Vec3(0f, 0f, -1f), Mat3.IDENTITY, 100f, 200f, 300f, 2.6f)
        assertTrue(front.scale > back.scale)
        assertTrue(front.alpha > back.alpha)
        assertEquals(200f, front.x, 1e-3f)
        assertEquals(300f, front.y, 1e-3f)
    }

    @Test fun drawOrderIsBackToFront() {
        val o = SphereMath.drawOrder(SphereMath.projectAll(SphereMath.fibonacci(20), Mat3.IDENTITY, 100f, 0f, 0f, 2.6f))
        assertTrue(o.zipWithNext().all { it.first.z <= it.second.z })
    }

    @Test fun hitTestPicksFrontMostAndIgnoresBack() {
        val p = listOf(
            Projected(0, 100f, 100f, 1f, 1f, 0.2f),
            Projected(1, 105f, 100f, 1f, 1f, 0.9f),
            Projected(2, 400f, 400f, 1f, 1f, -0.9f),
        )
        val sizes = listOf(60f, 60f, 60f)
        assertEquals(1, SphereMath.hitTest(p, sizes, 102f, 100f))
        assertNull(SphereMath.hitTest(p, sizes, 400f, 400f))
        assertNull(SphereMath.hitTest(p, sizes, 10f, 10f))
    }

    @Test fun flingDecaysAndStops() {
        val s = SphereState()
        s.fling(3f, 0f)
        repeat(600) { s.step(1f / 60f, 1.6f, 0f, false) }
        assertFalse(s.moving)
    }

    @Test fun dragKeepsOrthonormal() {
        val s = SphereState()
        repeat(500) { s.drag(0.05f, 0.03f) }
        val r = s.rot.m
        val len = sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2])
        assertTrue(abs(len - 1f) < 1e-2f)
    }
}
