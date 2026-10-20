package com.threejump.filterstudio.filterengine

/**
 * Detects the "channels shifted per pixel" corruption: neighbouring pixels differ hugely (alpha / other
 * channels land in R,G,B) while pixels 4 apart look alike. Natural images are the other way round.
 * Pure JVM code (unit-tested).
 */
object StripeCheck {
    /** Score >= this = corrupted frame. Natural photos score about 0.3-1. */
    const val LIMIT = 2f

    /** max over x and y of (mean |p(i) - p(i+1)| + 1) / (mean |p(i) - p(i+4)| + 1), on every 8th row/column. */
    fun score(px: IntArray, w: Int, h: Int): Float {
        var d1x = 0L; var d4x = 0L; var d1y = 0L; var d4y = 0L
        for (row in 0 until h step 8) {
            val o = row * w
            for (x in 0 until w - 4) { d1x += diff(px[o + x], px[o + x + 1]); d4x += diff(px[o + x], px[o + x + 4]) }
        }
        for (col in 0 until w step 8) {
            for (yy in 0 until h - 4) { d1y += diff(px[yy * w + col], px[(yy + 1) * w + col]); d4y += diff(px[yy * w + col], px[(yy + 4) * w + col]) }
        }
        val sx = (d1x + 1).toFloat() / (d4x + 1)
        val sy = (d1y + 1).toFloat() / (d4y + 1)
        return maxOf(sx, sy)
    }

    fun isStriped(px: IntArray, w: Int, h: Int) = score(px, w, h) >= LIMIT

    private fun diff(a: Int, b: Int): Int =
        kotlin.math.abs(((a shr 16) and 255) - ((b shr 16) and 255)) +
            kotlin.math.abs(((a shr 8) and 255) - ((b shr 8) and 255)) +
            kotlin.math.abs((a and 255) - (b and 255))
}
