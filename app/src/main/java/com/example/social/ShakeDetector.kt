package com.example.social

import kotlin.math.sqrt

/**
 * Pure-Kotlin shake detector (no Android deps, unit-tested).
 * Feed raw accelerometer samples (m/s², gravity included). Triggers when at least [minPeaks]
 * separate peaks above [thresholdG] occur within [windowMs]; then stays quiet for [debounceMs].
 */
class ShakeDetector(
    private val thresholdG: Double = 2.5,
    private val minPeaks: Int = 3,
    private val windowMs: Long = 1000,
    private val debounceMs: Long = 3000,
    private val minPeakGapMs: Long = 80
) {
    private val peaks = ArrayDeque<Long>()
    private var above = false
    private var lastTrigger = Long.MIN_VALUE / 2

    fun reset() { peaks.clear(); above = false; lastTrigger = Long.MIN_VALUE / 2 }

    /** @return true exactly once per detected shake. */
    fun onSample(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        val g = sqrt((x * x + y * y + z * z).toDouble()) / 9.80665
        val isAbove = g >= thresholdG
        var trigger = false
        if (isAbove && !above) { // rising edge = one peak
            if (peaks.isEmpty() || timeMs - peaks.last() >= minPeakGapMs) peaks.addLast(timeMs)
            while (peaks.isNotEmpty() && timeMs - peaks.first() > windowMs) peaks.removeFirst()
            if (peaks.size >= minPeaks && timeMs - lastTrigger >= debounceMs) {
                trigger = true; lastTrigger = timeMs; peaks.clear()
            }
        }
        above = isAbove
        return trigger
    }
}
