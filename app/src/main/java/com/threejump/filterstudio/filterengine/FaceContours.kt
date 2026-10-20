/*
 * Ordered MediaPipe Face Mesh contours (478-point topology) used to build the skin mask.
 * Index orders follow MediaPipe face_mesh_connections (Apache-2.0).
 * Licensed under the Apache License, Version 2.0.
 */
package com.threejump.filterstudio.filterengine

object FaceContours {
    val FACE_OVAL = intArrayOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
        152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109)
    val LIPS_OUTER = intArrayOf(61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185)
    val LIPS_INNER = intArrayOf(78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 310, 311, 312, 13, 82, 81, 80, 191)
    /** Cheek centres used for blush (subject's right / left). */
    const val CHEEK_RIGHT = 50
    const val CHEEK_LEFT = 280
    /** Upper eyelid lash line, outer corner -> inner corner (eyeliner + eyeshadow lower edge). */
    val RIGHT_UPPER_LID = intArrayOf(33, 246, 161, 160, 159, 158, 157, 173, 133)
    val LEFT_UPPER_LID = intArrayOf(263, 466, 388, 387, 386, 385, 384, 398, 362)
    /** Lower lash line, outer corner -> inner corner (lower lashes). */
    val RIGHT_LOWER_LID = intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133)
    val LEFT_LOWER_LID = intArrayOf(263, 249, 390, 373, 374, 380, 381, 382, 362)
    /** Lower brow edge, outer -> inner (eyeshadow upper limit). */
    val RIGHT_BROW_LOW = intArrayOf(46, 53, 52, 65, 55)
    val LEFT_BROW_LOW = intArrayOf(276, 283, 282, 295, 285)
    /** Analytic (gaussian) makeup centres: under-eye, highlight (forehead, nose bridge, chin), contour (cheek hollows). */
    const val UNDER_EYE_RIGHT = 230
    const val UNDER_EYE_LEFT = 450
    const val HL_FOREHEAD = 151
    const val HL_NOSE = 197
    const val HL_CHIN = 175
    const val CONTOUR_RIGHT = 147
    const val CONTOUR_LEFT = 376
    val LEFT_EYE = intArrayOf(362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398)
    val RIGHT_EYE = intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)
    val LEFT_BROW = intArrayOf(336, 296, 334, 293, 300, 276, 283, 282, 295, 285)
    val RIGHT_BROW = intArrayOf(70, 63, 105, 66, 107, 55, 65, 52, 53, 46)

    /**
     * Eyeliner wing tip (pixels) from outer corner (ox, oy) and eye axis (dx, dy) = outer - inner corner:
     * the axis rotated toward the top of the image (-y) by [angleDeg], [length] x 0.19 of the eye width.
     * null when length <= 0. Pure Kotlin.
     */
    fun wingTip(ox: Float, oy: Float, dx: Float, dy: Float, length: Float, angleDeg: Float): FloatArray? {
        val l = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length <= 0f || l < 1e-4f) return null
        val ux = dx / l; val uy = dy / l
        // perpendicular pointing up in the image
        var px = uy; var py = -ux
        if (py > 0f) { px = -px; py = -py }
        val a = Math.toRadians(angleDeg.toDouble())
        val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
        val r = l * 0.19f * length
        return floatArrayOf(ox + (ux * c + px * s) * r, oy + (uy * c + py * s) * r)
    }

    /**
     * Writes contour [idx] as pixel coords into [out] (x,y pairs) scaled by [scale] around its centroid.
     * points: normalized [x,y,z]*N. Pure Kotlin. Returns number of points written.
     */
    fun contour(points: FloatArray, idx: IntArray, w: Float, h: Float, scale: Float, out: FloatArray): Int {
        var cx = 0f; var cy = 0f; var n = 0
        for (i in idx) if (i * 3 + 1 < points.size) { cx += points[i * 3]; cy += points[i * 3 + 1]; n++ }
        if (n == 0) return 0
        cx /= n; cy /= n
        var k = 0
        for (i in idx) if (i * 3 + 1 < points.size) {
            out[k * 2] = (cx + (points[i * 3] - cx) * scale) * w
            out[k * 2 + 1] = (cy + (points[i * 3 + 1] - cy) * scale) * h
            k++
        }
        return k
    }
}
