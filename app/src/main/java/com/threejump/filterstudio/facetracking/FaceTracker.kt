/*
 * Portions adapted from Google MediaPipe Samples FaceLandmarkerHelper.kt
 * Copyright 2023 The TensorFlow Authors. Licensed under the Apache License, Version 2.0.
 * Modified by 3Jump Filter Studio: GPU->CPU auto fallback, smoothing, drop-when-busy,
 * rotation via ImageProcessingOptions, timing.
 */
package com.threejump.filterstudio.facetracking

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean

enum class TrackerDelegate { GPU, CPU }

/**
 * Landmarks normalized to the UPRIGHT (rotated) camera image, NOT mirrored.
 * Mirror at draw time for the selfie preview.
 */
data class FaceFrame(
    val points: FloatArray?,
    val imageWidth: Int,      // upright width
    val imageHeight: Int,     // upright height
    val inferenceMs: Long,
    val frameTimeMs: Long,    // SystemClock.uptimeMillis() when the camera frame reached the analyzer
    val delegate: TrackerDelegate,
    /** The upright camera bitmap this result belongs to (for filter rendering); null if unavailable. */
    val image: Bitmap? = null,
)

/** Must be created, used and closed on ONE thread (GPU delegate requirement). */
class FaceTracker(
    private val context: Context,
    private val listener: Listener,
    preferGpu: Boolean = true,
    var smoothingEnabled: Boolean = true,
) {
    interface Listener {
        fun onFace(frame: FaceFrame)
        fun onTrackerError(message: String)
        fun onDelegateChanged(delegate: TrackerDelegate, reason: String?)
    }

    var delegate: TrackerDelegate = if (preferGpu) TrackerDelegate.GPU else TrackerDelegate.CPU
        private set
    private var landmarker: FaceLandmarker? = null
    private val smoother = LandmarkSmoother(fastIndices = FaceRegion.LIPS.indices)
    private var lastTs = 0L
    @Volatile private var needsCpuFallback = false
    private val busy = AtomicBoolean(false)
    @Volatile private var inflightStart = 0L
    @Volatile private var inflightFrameTime = 0L
    @Volatile private var inflightRotation = 0
    /** The bitmap passed to [detect] for the frame in flight (not reused by the camera until the result). */
    @Volatile private var inflightBitmap: Bitmap? = null

    /** Problems found comparing our region index sets with MediaPipe's (empty = identical). */
    val regionCheck: List<String>

    init { setup(); regionCheck = MediaPipeRegionCheck.run().also { if (it.isNotEmpty()) Log.w(TAG, "region check: $it") } }

    /** Call on tracker thread. */
    fun setSmoothing(minCutoff: Double, beta: Double) { smoother.minCutoff = minCutoff; smoother.beta = beta }

    /** True while the model is processing a frame; callers should drop new frames. */
    val isBusy: Boolean get() = busy.get() && SystemClock.uptimeMillis() - inflightStart < 1000

    private fun setup() {
        landmarker?.close(); landmarker = null
        try {
            landmarker = build(delegate)
        } catch (t: Throwable) {
            Log.w(TAG, "init failed on $delegate", t)
            if (delegate == TrackerDelegate.GPU) {
                delegate = TrackerDelegate.CPU
                listener.onDelegateChanged(delegate, "GPU init failed: ${t.message}")
                try { landmarker = build(delegate) } catch (t2: Throwable) {
                    listener.onTrackerError("CPU init failed: ${t2.message}")
                }
            } else listener.onTrackerError("init failed: ${t.message}")
        }
        if (landmarker != null) listener.onDelegateChanged(delegate, null)
    }

    private fun build(d: TrackerDelegate): FaceLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL)
            .setDelegate(if (d == TrackerDelegate.GPU) Delegate.GPU else Delegate.CPU)
            .build()
        val opts = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputFaceBlendshapes(false)
            .setResultListener(this::onResult)
            .setErrorListener { e ->
                Log.e(TAG, "runtime error on $delegate", e)
                busy.set(false)
                if (delegate == TrackerDelegate.GPU) needsCpuFallback = true
                else listener.onTrackerError(e.message ?: "error")
            }
            .build()
        return FaceLandmarker.createFromOptions(context, opts)
    }

    /**
     * @param bitmap raw (unrotated, unmirrored) camera frame; may be reused by the caller
     *               only after the result for this frame arrives (enforced by [isBusy]).
     * @param rotationDegrees CameraX imageInfo.rotationDegrees.
     * @return false if the frame was dropped.
     */
    fun detect(bitmap: Bitmap, rotationDegrees: Int, frameTimeMs: Long): Boolean {
        if (needsCpuFallback) {
            needsCpuFallback = false
            delegate = TrackerDelegate.CPU
            listener.onDelegateChanged(delegate, "GPU runtime error")
            setup()
        }
        val lm = landmarker ?: return false
        if (isBusy) return false
        var ts = SystemClock.uptimeMillis()
        if (ts <= lastTs) ts = lastTs + 1
        lastTs = ts
        busy.set(true)
        inflightStart = ts; inflightFrameTime = frameTimeMs; inflightRotation = rotationDegrees; inflightBitmap = bitmap
        val mp: MPImage = BitmapImageBuilder(bitmap).build()
        val ipo = ImageProcessingOptions.builder().setRotationDegrees(rotationDegrees).build()
        return try {
            lm.detectAsync(mp, ipo, ts); true
        } catch (t: Throwable) {
            Log.e(TAG, "detectAsync failed", t)
            busy.set(false)
            if (delegate == TrackerDelegate.GPU) needsCpuFallback = true
            false
        }
    }

    private fun onResult(result: FaceLandmarkerResult, input: MPImage) {
        val now = SystemClock.uptimeMillis()
        val ms = now - inflightStart
        val frameTime = inflightFrameTime
        val swap = inflightRotation % 180 != 0
        val w = if (swap) input.height else input.width
        val h = if (swap) input.width else input.height
        // Our own frame, NOT the MPImage MediaPipe hands back: on some devices that one comes back with the
        // RGBA bytes read 3 per pixel (colour stripes every 4 px), see StripeCheck.
        val image = inflightBitmap
        busy.set(false)
        val faces = result.faceLandmarks()
        if (faces.isEmpty()) {
            smoother.reset()
            listener.onFace(FaceFrame(null, w, h, ms, frameTime, delegate, image))
            return
        }
        val face = faces[0]
        val raw = FloatArray(face.size * 3)
        for (i in face.indices) {
            raw[i * 3] = face[i].x(); raw[i * 3 + 1] = face[i].y(); raw[i * 3 + 2] = face[i].z()
        }
        val pts = if (smoothingEnabled) smoother.smooth(raw, now / 1000.0) else raw
        listener.onFace(FaceFrame(pts, w, h, ms, frameTime, delegate, image))
    }

    fun close() { landmarker?.close(); landmarker = null }

    companion object {
        private const val TAG = "FaceTracker"
        const val MODEL = "face_landmarker.task"
    }
}
