package com.threejump.filterstudio.facetracking

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One Euro Filter (Casiez, Roussel, Vogel, CHI 2012). Pure Kotlin, no Android deps.
 * Low speed -> strong smoothing (less jitter); high speed -> less lag.
 */
class OneEuroFilter(
    var minCutoff: Double = 1.0,
    var beta: Double = 0.0,
    var dCutoff: Double = 1.0,
) {
    private var xPrev = 0.0
    private var dxPrev = 0.0
    private var tPrev = -1.0
    private var initialized = false

    fun filter(x: Double, tSeconds: Double): Double {
        if (!initialized) {
            initialized = true; xPrev = x; dxPrev = 0.0; tPrev = tSeconds
            return x
        }
        val dt = (tSeconds - tPrev).let { if (it <= 0.0) 1e-3 else it }
        val dx = (x - xPrev) / dt
        val dxHat = lerp(dxPrev, dx, alpha(dCutoff, dt))
        val xHat = lerp(xPrev, x, alpha(minCutoff + beta * abs(dxHat), dt))
        xPrev = xHat; dxPrev = dxHat; tPrev = tSeconds
        return xHat
    }

    fun reset() { initialized = false }

    companion object {
        fun alpha(cutoff: Double, dt: Double): Double = 1.0 / (1.0 + 1.0 / (2 * PI * cutoff * dt))
        fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    }
}

/**
 * Face-level velocity-adaptive One Euro smoother.
 * Speed is estimated from the face centroid (averaging 478 points cancels per-point
 * noise), and the SAME adaptive cutoff is applied to every non-lip point, so the whole mask
 * snaps to the face on fast moves while still damping jitter when the head is still.
 * Lip points ([fastIndices]) get their own per-coordinate One Euro filters with lighter
 * smoothing so the mouth follows smiles / opening. Large jumps or gaps reset immediately.
 * Speeds are in normalized image units per second.
 */
class LandmarkSmoother(
    var minCutoff: Double = DEFAULT_MIN_CUTOFF,
    var beta: Double = DEFAULT_BETA,
    var dCutoff: Double = DEFAULT_D_CUTOFF,
    fastIndices: IntArray = IntArray(0),
    var fastMinCutoffFactor: Double = 2.5,
    var fastBetaFactor: Double = 2.0,
    var resetJump: Double = DEFAULT_RESET_JUMP,
    var resetGapSeconds: Double = DEFAULT_RESET_GAP_S,
) {
    private val fastIdx = fastIndices.copyOf()
    private val isFast = BooleanArray(MAX_POINTS).also { a -> fastIdx.forEach { if (it in a.indices) a[it] = true } }
    private var fastFilters: Array<OneEuroFilter> = emptyArray()
    private var prev: FloatArray? = null
    private var cxPrev = 0.0; private var cyPrev = 0.0
    private var speedPrev = 0.0
    private var tPrev = 0.0

    var lastCutoff = 0.0; private set
    /** Number of automatic resets (jump / re-acquire), for diagnostics. */
    var resets = 0; private set

    fun smooth(points: FloatArray, tSeconds: Double): FloatArray {
        val n = points.size / 3
        var cx = 0.0; var cy = 0.0
        for (i in 0 until n) { cx += points[i * 3]; cy += points[i * 3 + 1] }
        if (n > 0) { cx /= n; cy /= n }
        val p = prev
        val dt0 = tSeconds - tPrev
        val jump = if (p == null) 0.0 else sqrt((cx - cxPrev) * (cx - cxPrev) + (cy - cyPrev) * (cy - cyPrev))
        if (p == null || p.size != points.size || dt0 > resetGapSeconds || jump > resetJump) {
            if (p != null) resets++
            return restart(points, cx, cy, tSeconds)
        }
        val dt = if (dt0 <= 0.0) 1e-3 else dt0
        val sHat = OneEuroFilter.lerp(speedPrev, jump / dt, OneEuroFilter.alpha(dCutoff, dt))
        val cutoff = minCutoff + beta * sHat
        val a = OneEuroFilter.alpha(cutoff, dt).toFloat()
        val out = FloatArray(points.size)
        for (i in points.indices) out[i] = p[i] + (points[i] - p[i]) * a
        // lips: per-point, lighter smoothing
        val fMin = minCutoff * fastMinCutoffFactor; val fBeta = beta * fastBetaFactor
        for (k in fastIdx.indices) {
            val idx = fastIdx[k]; if (idx >= n) continue
            for (c in 0 until 3) {
                val f = fastFilters[k * 3 + c]; f.minCutoff = fMin; f.beta = fBeta; f.dCutoff = dCutoff
                out[idx * 3 + c] = f.filter(points[idx * 3 + c].toDouble(), tSeconds).toFloat()
            }
        }
        prev = out; cxPrev = cx; cyPrev = cy; speedPrev = sHat; tPrev = tSeconds; lastCutoff = cutoff
        return out
    }

    private fun restart(points: FloatArray, cx: Double, cy: Double, t: Double): FloatArray {
        prev = points.copyOf(); cxPrev = cx; cyPrev = cy; speedPrev = 0.0; tPrev = t; lastCutoff = minCutoff
        if (fastFilters.size != fastIdx.size * 3) fastFilters = Array(fastIdx.size * 3) { OneEuroFilter() }
        fastFilters.forEach { it.reset() }
        val n = points.size / 3
        for (k in fastIdx.indices) { val idx = fastIdx[k]; if (idx < n) for (c in 0 until 3) fastFilters[k * 3 + c].filter(points[idx * 3 + c].toDouble(), t) }
        return points.copyOf()
    }

    fun reset() { prev = null }

    companion object {
        const val DEFAULT_MIN_CUTOFF = 4.0
        const val DEFAULT_BETA = 80.0
        const val DEFAULT_D_CUTOFF = 4.0
        /** Centroid jump (normalized) between two results that forces an immediate reset. */
        const val DEFAULT_RESET_JUMP = 0.12
        /** Gap without results that counts as re-acquisition. */
        const val DEFAULT_RESET_GAP_S = 0.3
        const val MAX_POINTS = 478
    }
}
