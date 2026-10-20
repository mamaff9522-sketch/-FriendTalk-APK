/*
 * Builds the soft skin mask (red channel = apply filter) from 478 face landmarks with Android Canvas.
 * Licensed under the Apache License, Version 2.0.
 */
package com.threejump.filterstudio.filterengine

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Mask 1 [bitmap]:  R = skin (filters apply), G = lips (lipstick, outer minus inner mouth), B = brows (brow fill).
 * Mask 2 [bitmap2]: R = eyeshadow (upper lid -> under brow, eye opening cut out), G = eyeliner (upper lash line
 *                   stroke + small wing), B = mouth opening (teeth whitening; the shader only whitens bright, low-saturation pixels).
 * Mask 3 [bitmap3]: R = upper eyelashes, G = lower eyelashes (scaled by eyelashLower). Thin curved strokes
 *                   growing outward from the lash lines, tilted toward the outer corner.
 * Mask is in the SAME normalized space as the camera image (upright, not mirrored), so the shader
 * samples image and mask with the same UV. Reuses one bitmap; call from one thread.
 */
class FaceMaskBuilder(private val maskWidth: Int = 240) {
    var bitmap: Bitmap? = null; private set
    /** Second mask (eye makeup + teeth); built by [build] in the same call. */
    var bitmap2: Bitmap? = null; private set
    private var canvas: Canvas? = null
    private var canvas2: Canvas? = null
    /** Third mask (eyelashes); built by [build] in the same call. */
    var bitmap3: Bitmap? = null; private set
    private var canvas3: Canvas? = null
    private val lashUpper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 0, 0); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD) }
    private val lashLower = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 255, 0); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD) }
    private val shadowAdd = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 0, 0); xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD) }
    private val eyeCut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 255, 255); xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY) }
    private val linerAdd = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 255, 0); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD) }
    private val teethAdd = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 0, 255); xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD) }
    private val shadowPts = FloatArray(32)
    private var shadowBlur = -1f; private var linerBlur = -1f
    private val path = Path()
    private val buf = FloatArray(64 * 2)
    // skin goes into R ONLY: white would also fill G (lips) and B (brows) and put lipstick / brow colour on the whole face
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; style = Paint.Style.FILL }
    private val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val lipAdd = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 255, 0); xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD) }
    private val lipInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 0, 255); xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY) }
    private val browAdd = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 0, 255); xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD) }
    private var makeupBlur = -1f
    private var fillBlur = -1f
    private var cutBlur = -1f

    /** points: normalized [x,y,z]*478 or null (no face -> empty mask). imageW/H: camera image size. */
    fun build(points: FloatArray?, imageW: Int, imageH: Int, cfg: FilterConfig): Bitmap {
        val w = maskWidth
        val h = max(1, (maskWidth.toFloat() * imageH / imageW).roundToInt())
        var b = bitmap
        if (b == null || b.width != w || b.height != h) {
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); bitmap = b; canvas = Canvas(b)
            val b2 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); bitmap2 = b2; canvas2 = Canvas(b2)
            val b3 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); bitmap3 = b3; canvas3 = Canvas(b3)
        }
        b!!.eraseColor(Color.BLACK)
        bitmap2!!.eraseColor(Color.BLACK)
        bitmap3!!.eraseColor(Color.BLACK)
        if (points == null || points.size < 468 * 3) return b
        val cv = canvas!!
        val wf = w.toFloat(); val hf = h.toFloat()
        // face width (px) for scale-relative blur
        var minX = 1f; var maxX = 0f
        for (i in FaceContours.FACE_OVAL) { val x = points[i * 3]; if (x < minX) minX = x; if (x > maxX) maxX = x }
        val faceW = max(1f, (maxX - minX) * wf)

        val fb = max(0.5f, cfg["feather"] * faceW * 0.15f)
        if (fb != fillBlur) { fill.maskFilter = BlurMaskFilter(fb, BlurMaskFilter.Blur.NORMAL); fillBlur = fb }
        draw(cv, points, FaceContours.FACE_OVAL, wf, hf, 1f + cfg["expand"], fill)

        val cb = max(0.5f, faceW * 0.025f)
        if (cb != cutBlur) { cut.maskFilter = BlurMaskFilter(cb, BlurMaskFilter.Blur.NORMAL); cutBlur = cb }
        val eye = (cfg["eyeProtect"] * 255f).roundToInt().coerceIn(0, 255)
        val lip = (cfg["lipProtect"] * 255f).roundToInt().coerceIn(0, 255)
        if (eye > 0) {
            cut.alpha = eye
            draw(cv, points, FaceContours.LEFT_EYE, wf, hf, 1.35f, cut)
            draw(cv, points, FaceContours.RIGHT_EYE, wf, hf, 1.35f, cut)
            draw(cv, points, FaceContours.LEFT_BROW, wf, hf, 1.25f, cut)
            draw(cv, points, FaceContours.RIGHT_BROW, wf, hf, 1.25f, cut)
        }
        if (lip > 0) { cut.alpha = lip; draw(cv, points, FaceContours.LIPS_OUTER, wf, hf, 1.1f, cut) }
        // makeup channels (only if used, keeps cost low)
        val mb = max(0.5f, faceW * 0.012f)
        if (mb != makeupBlur) { val f = BlurMaskFilter(mb, BlurMaskFilter.Blur.NORMAL); lipAdd.maskFilter = f; lipInner.maskFilter = f; browAdd.maskFilter = f; makeupBlur = mb }
        if (cfg.isOn(FilterGroup.LIPSTICK)) {
            draw(cv, points, FaceContours.LIPS_OUTER, wf, hf, 1f, lipAdd)
            draw(cv, points, FaceContours.LIPS_INNER, wf, hf, 1f, lipInner)
        }
        if (cfg.isOn(FilterGroup.BROW)) {
            draw(cv, points, FaceContours.LEFT_BROW, wf, hf, 1.05f, browAdd)
            draw(cv, points, FaceContours.RIGHT_BROW, wf, hf, 1.05f, browAdd)
        }
        buildMask2(canvas2!!, points, wf, hf, faceW, cfg)
        if (cfg.isOn(FilterGroup.EYELASH)) buildLashes(canvas3!!, points, wf, hf, faceW, cfg)
        return b
    }

    private fun buildLashes(cv: Canvas, pts: FloatArray, wf: Float, hf: Float, faceW: Float, cfg: FilterConfig) {
        val len = faceW * 0.055f * cfg["eyelashLength"]
        val width = max(0.8f, faceW * 0.007f)
        lashUpper.strokeWidth = width; lashLower.strokeWidth = width * 0.8f
        val lower = (cfg["eyelashLower"] * 255f).roundToInt().coerceIn(0, 255)
        lashes(cv, pts, FaceContours.RIGHT_UPPER_LID, FaceContours.RIGHT_EYE, wf, hf, len, 3, lashUpper)
        lashes(cv, pts, FaceContours.LEFT_UPPER_LID, FaceContours.LEFT_EYE, wf, hf, len, 3, lashUpper)
        if (lower > 0) {
            lashLower.color = Color.rgb(0, lower, 0)
            lashes(cv, pts, FaceContours.RIGHT_LOWER_LID, FaceContours.RIGHT_EYE, wf, hf, len * 0.45f, 2, lashLower)
            lashes(cv, pts, FaceContours.LEFT_LOWER_LID, FaceContours.LEFT_EYE, wf, hf, len * 0.45f, 2, lashLower)
        }
    }

    /**
     * Lashes along [lid] (outer -> inner corner), [perSeg] per segment: each grows away from the eye centre,
     * tilted toward the outer corner (more at the outer end), longest at the outer end, slightly curled.
     */
    private fun lashes(cv: Canvas, p: FloatArray, lid: IntArray, eye: IntArray, w: Float, h: Float, len: Float, perSeg: Int, paint: Paint) {
        var cx = 0f; var cy = 0f
        for (i in eye) { cx += p[i * 3] * w; cy += p[i * 3 + 1] * h }
        cx /= eye.size; cy /= eye.size
        val ox0 = p[lid.first() * 3] * w; val oy0 = p[lid.first() * 3 + 1] * h
        var odx = ox0 - p[lid.last() * 3] * w; var ody = oy0 - p[lid.last() * 3 + 1] * h
        val ol = kotlin.math.sqrt(odx * odx + ody * ody); if (ol < 1e-3f) return
        odx /= ol; ody /= ol
        val n = (lid.size - 1) * perSeg
        for (k in 1 until n) {   // skip the exact corners
            val seg = k / perSeg; val f = (k % perSeg).toFloat() / perSeg
            val a = lid[seg]; val b = lid[seg + 1]
            val x = (p[a * 3] + (p[b * 3] - p[a * 3]) * f) * w
            val y = (p[a * 3 + 1] + (p[b * 3 + 1] - p[a * 3 + 1]) * f) * h
            val t = k.toFloat() / n   // 0 outer .. 1 inner
            var dx = x - cx; var dy = y - cy
            val dl = kotlin.math.sqrt(dx * dx + dy * dy); if (dl < 1e-3f) continue
            dx /= dl; dy /= dl
            val tilt = 0.7f * (1f - t)
            dx += odx * tilt; dy += ody * tilt
            val nl = kotlin.math.sqrt(dx * dx + dy * dy); dx /= nl; dy /= nl
            val l = len * (0.6f + 0.4f * (1f - t))
            path.rewind(); path.moveTo(x, y)
            path.quadTo(x + dx * l * 0.55f, y + dy * l * 0.55f, x + (dx + odx * 0.35f) * l, y + (dy + ody * 0.35f) * l)
            cv.drawPath(path, paint)
        }
    }

    private fun buildMask2(cv: Canvas, pts: FloatArray, wf: Float, hf: Float, faceW: Float, cfg: FilterConfig) {
        if (cfg.isOn(FilterGroup.EYESHADOW)) {
            val sb = max(0.5f, faceW * 0.03f)
            if (sb != shadowBlur) { val f = BlurMaskFilter(sb, BlurMaskFilter.Blur.NORMAL); shadowAdd.maskFilter = f; shadowBlur = sb }
            // height: fraction of the lid->brow distance covered (size 1 = 45 %)
            val t = (0.45f * cfg["eyeshadowSize"]).coerceIn(0.15f, 0.9f)
            shadow(cv, pts, FaceContours.RIGHT_UPPER_LID, FaceContours.RIGHT_BROW_LOW, wf, hf, t)
            shadow(cv, pts, FaceContours.LEFT_UPPER_LID, FaceContours.LEFT_BROW_LOW, wf, hf, t)
        }
        if (cfg.isOn(FilterGroup.EYELINER)) {
            val lb = max(0.5f, faceW * 0.004f)
            if (lb != linerBlur) { linerAdd.maskFilter = BlurMaskFilter(lb, BlurMaskFilter.Blur.NORMAL); linerBlur = lb }
            linerAdd.strokeWidth = max(1f, faceW * 0.012f * cfg["eyelinerWidth"])
            val wingLen = cfg["eyelinerWing"]; val wingAngle = cfg["eyelinerWingAngle"]
            liner(cv, pts, FaceContours.RIGHT_UPPER_LID, wf, hf, wingLen, wingAngle)
            liner(cv, pts, FaceContours.LEFT_UPPER_LID, wf, hf, wingLen, wingAngle)
        }
        // never paint makeup inside the eye opening (protects iris / eye white)
        if (cfg.isOn(FilterGroup.EYESHADOW) || cfg.isOn(FilterGroup.EYELINER)) {
            eyeCut.maskFilter = null
            draw(cv, pts, FaceContours.RIGHT_EYE, wf, hf, 0.92f, eyeCut)
            draw(cv, pts, FaceContours.LEFT_EYE, wf, hf, 0.92f, eyeCut)
        }
        if (cfg.isOn(FilterGroup.TEETH)) draw(cv, pts, FaceContours.LIPS_INNER, wf, hf, 0.95f, teethAdd)
    }

    /** Polygon: lash line (outer->inner) then the lid->brow interpolated line back (inner->outer). */
    private fun shadow(cv: Canvas, p: FloatArray, lid: IntArray, brow: IntArray, w: Float, h: Float, t: Float) {
        path.rewind()
        for ((k, i) in lid.withIndex()) { val x = p[i * 3] * w; val y = p[i * 3 + 1] * h; if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        for (k in brow.indices.reversed()) {
            // pair each brow point with the lid point at the same relative position
            val li = lid[(k * (lid.size - 1)) / (brow.size - 1)]; val bi = brow[k]
            val x = p[li * 3] + (p[bi * 3] - p[li * 3]) * t; val y = p[li * 3 + 1] + (p[bi * 3 + 1] - p[li * 3 + 1]) * t
            path.lineTo(x * w, y * h)
        }
        path.close(); cv.drawPath(path, shadowAdd)
    }

    /** Stroke along the upper lash line plus a short upward wing past the outer corner. */
    /**
     * Wing: from the outer corner along the eye axis (inner -> outer), rotated upward by [wingAngle] degrees,
     * [wingLen] x 19 % of the eye width long (0 = no wing). Defaults (1, 30°) match the previous fixed wing.
     */
    private fun liner(cv: Canvas, p: FloatArray, lid: IntArray, w: Float, h: Float, wingLen: Float, wingAngle: Float) {
        val o = lid.first(); val inn = lid.last()
        val ox = p[o * 3] * w; val oy = p[o * 3 + 1] * h
        val dx = ox - p[inn * 3] * w; val dy = oy - p[inn * 3 + 1] * h
        path.rewind()
        val tip = FaceContours.wingTip(ox, oy, dx, dy, wingLen, wingAngle)
        if (tip != null) { path.moveTo(tip[0], tip[1]); path.lineTo(ox, oy) } else path.moveTo(ox, oy)
        for (i in lid) path.lineTo(p[i * 3] * w, p[i * 3 + 1] * h)
        cv.drawPath(path, linerAdd)
    }

    private fun draw(cv: Canvas, pts: FloatArray, idx: IntArray, w: Float, h: Float, scale: Float, p: Paint) {
        val n = FaceContours.contour(pts, idx, w, h, scale, buf)
        if (n < 3) return
        path.rewind(); path.moveTo(buf[0], buf[1])
        for (k in 1 until n) path.lineTo(buf[k * 2], buf[k * 2 + 1])
        path.close()
        cv.drawPath(path, p)
    }
}
