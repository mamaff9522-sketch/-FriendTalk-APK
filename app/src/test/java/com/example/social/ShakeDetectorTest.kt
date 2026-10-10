package com.example.social

import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeDetectorTest {
    private val g = 9.80665f

    /** Feeds samples every 20 ms; [gAt] gives the total g-force at time t. Returns trigger count. */
    private fun run(d: ShakeDetector, fromMs: Long, toMs: Long, gAt: (Long) -> Float): Int {
        var n = 0; var t = fromMs
        while (t <= toMs) { if (d.onSample(0f, 0f, gAt(t) * g, t)) n++; t += 20 }
        return n
    }
    private fun shakeG(start: Long, peaks: Int, periodMs: Long = 200) = { t: Long ->
        val k = (t - start)
        if (k >= 0 && k < peaks * periodMs && (k % periodMs) < 60) 3.2f else 1f
    }

    @Test fun stillPhoneNeverTriggers() = assertEquals(0, run(ShakeDetector(), 0, 10_000) { 1f })

    @Test fun singleBumpDoesNotTrigger() = assertEquals(0, run(ShakeDetector(), 0, 3000) { t -> if (t in 1000..1080) 4f else 1f })

    @Test fun tapSpikeDoesNotTrigger() = assertEquals(0, run(ShakeDetector(), 0, 3000) { t -> if (t == 1500L) 6f else 1f })

    @Test fun twoBumpsDoNotTrigger() = assertEquals(0, run(ShakeDetector(), 0, 4000, shakeG(1000, 2)))

    @Test fun peaksSpreadOverMoreThanOneSecondDoNotTrigger() =
        assertEquals(0, run(ShakeDetector(), 0, 6000, shakeG(1000, 4, periodMs = 700)))

    @Test fun moderateMotionBelowThresholdDoesNotTrigger() =
        assertEquals(0, run(ShakeDetector(), 0, 5000) { t -> if ((t / 100) % 2 == 0L) 2.0f else 1f })

    @Test fun realShakeTriggersOnce() = assertEquals(1, run(ShakeDetector(), 0, 4000, shakeG(1000, 8)))

    @Test fun debounceBlocksSecondShakeWithin3s() {
        val d = ShakeDetector()
        val a = shakeG(1000, 6); val b = shakeG(2600, 6)
        assertEquals(1, run(d, 0, 5000) { t -> maxOf(a(t), b(t)) })
    }

    @Test fun secondShakeAfterDebounceTriggersAgain() {
        val d = ShakeDetector()
        val a = shakeG(1000, 6); val b = shakeG(5000, 6)
        assertEquals(2, run(d, 0, 7000) { t -> maxOf(a(t), b(t)) })
    }
}
