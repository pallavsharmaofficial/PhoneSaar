package dev.pallav.phonesaar.launcher.sphere

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float)

/** Row-major 3x3 rotation matrix. */
class Mat3(val m: FloatArray) {
    fun apply(v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z,
    )

    operator fun times(o: Mat3): Mat3 {
        val r = FloatArray(9)
        for (i in 0..2) for (j in 0..2) r[i * 3 + j] = m[i * 3] * o.m[j] + m[i * 3 + 1] * o.m[3 + j] + m[i * 3 + 2] * o.m[6 + j]
        return Mat3(r)
    }

    /** Gram-Schmidt so repeated drags never skew the sphere. */
    fun orthonormalized(): Mat3 {
        fun norm(a: FloatArray): FloatArray { val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return if (l == 0f) a else floatArrayOf(a[0] / l, a[1] / l, a[2] / l) }
        val r0 = norm(floatArrayOf(m[0], m[1], m[2]))
        var r1 = floatArrayOf(m[3], m[4], m[5])
        val d = r0[0] * r1[0] + r0[1] * r1[1] + r0[2] * r1[2]
        r1 = norm(floatArrayOf(r1[0] - d * r0[0], r1[1] - d * r0[1], r1[2] - d * r0[2]))
        val r2 = floatArrayOf(r0[1] * r1[2] - r0[2] * r1[1], r0[2] * r1[0] - r0[0] * r1[2], r0[0] * r1[1] - r0[1] * r1[0])
        return Mat3(floatArrayOf(r0[0], r0[1], r0[2], r1[0], r1[1], r1[2], r2[0], r2[1], r2[2]))
    }

    companion object {
        val IDENTITY get() = Mat3(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
        fun aboutY(a: Float): Mat3 { val c = cos(a); val s = sin(a); return Mat3(floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c)) }
        fun aboutX(a: Float): Mat3 { val c = cos(a); val s = sin(a); return Mat3(floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c)) }
    }
}

/** A point after rotation and perspective: screen position, scale, alpha and depth (z>0 faces the viewer). */
data class Projected(val index: Int, val x: Float, val y: Float, val scale: Float, val alpha: Float, val z: Float)

object SphereMath {
    private val GOLDEN = PI * (3.0 - sqrt(5.0))

    /** Fibonacci sphere: n near-evenly spaced unit vectors, y from top to bottom. */
    fun fibonacci(n: Int): List<Vec3> {
        if (n <= 0) return emptyList()
        if (n == 1) return listOf(Vec3(0f, 0f, 1f))
        return List(n) { i ->
            val y = 1.0 - 2.0 * (i + 0.5) / n
            val r = sqrt(1.0 - y * y)
            val t = GOLDEN * i
            Vec3((cos(t) * r).toFloat(), y.toFloat(), (sin(t) * r).toFloat())
        }
    }

    /** Points reordered so rank 0 is closest to the front (0,0,1); rank i gets the i-th nearest point. */
    fun frontFirst(points: List<Vec3>): List<Vec3> = points.sortedByDescending { it.z }

    /** Icon size in dp for a rank: biggest first, easing down to min. */
    fun sizeForRank(rank: Int, count: Int, minDp: Float, maxDp: Float): Float {
        if (count <= 1) return maxDp
        val t = rank.toFloat() / (count - 1)
        return minDp + (maxDp - minDp) * (1f - t) * (1f - t * 0.35f)
    }

    /** Perspective: camera at distance `perspective` radii; front points are larger and opaque, back ones small and faded. */
    fun project(index: Int, v: Vec3, rot: Mat3, radiusPx: Float, cx: Float, cy: Float, perspective: Float): Projected {
        val p = rot.apply(v)
        val scale = perspective / (perspective - p.z)
        return Projected(index, cx + p.x * radiusPx * scale, cy + p.y * radiusPx * scale, scale, (0.35f + 0.65f * (p.z + 1f) / 2f).coerceIn(0f, 1f), p.z)
    }

    fun projectAll(points: List<Vec3>, rot: Mat3, radiusPx: Float, cx: Float, cy: Float, perspective: Float): List<Projected> =
        points.mapIndexed { i, v -> project(i, v, rot, radiusPx, cx, cy, perspective) }

    /** Back-to-front draw order. */
    fun drawOrder(p: List<Projected>): List<Projected> = p.sortedBy { it.z }

    /** The front-most icon whose box contains the touch, or null; back-side icons are not tappable. */
    fun hitTest(p: List<Projected>, sizesPx: List<Float>, tx: Float, ty: Float): Int? {
        var best: Projected? = null
        for (q in p) {
            if (q.z < -0.1f) continue
            val half = sizesPx[q.index] * q.scale / 2f
            if (abs(tx - q.x) <= half && abs(ty - q.y) <= half && (best == null || q.z > best.z)) best = q
        }
        return best?.index
    }
}

/** Orientation plus angular velocity; drag adds rotation about the screen axes and a fling keeps it turning. */
class SphereState {
    var rot: Mat3 = Mat3.IDENTITY
    private var wy = 0f
    private var wx = 0f
    private var steps = 0

    fun drag(dxRad: Float, dyRad: Float) {
        rot = (Mat3.aboutX(-dyRad) * Mat3.aboutY(dxRad) * rot).also { if (++steps % 32 == 0) rot = it.orthonormalized() }
    }

    fun fling(vxRad: Float, vyRad: Float) { wy = vxRad; wx = vyRad }
    fun stopMotion() { wy = 0f; wx = 0f }

    /** Advances dt seconds; friction is the exponential decay rate of the fling, autoRotate is deg/s about Y. */
    fun step(dt: Float, friction: Float, autoRotateDegPerSec: Float, dragging: Boolean) {
        if (dragging) return
        val decay = kotlin.math.exp(-friction * dt)
        wy *= decay; wx *= decay
        if (abs(wy) < 1e-3f) wy = 0f
        if (abs(wx) < 1e-3f) wx = 0f
        val auto = (autoRotateDegPerSec * PI / 180.0).toFloat()
        val ay = wy + auto
        if (ay != 0f || wx != 0f) drag(ay * dt, wx * dt)
    }

    val moving get() = wy != 0f || wx != 0f
}
