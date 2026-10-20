package com.threejump.filterstudio.facetracking

import com.google.mediapipe.tasks.components.containers.Connection
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker as C

/** Device-only: compares [FaceRegions] with tasks-vision FaceLandmarker connection sets. Returns problems (empty = OK). */
object MediaPipeRegionCheck {
    private fun idx(vararg sets: Set<Connection>, extra: Int? = null): Set<Int> {
        val s = HashSet<Int>(); sets.forEach { set -> set.forEach { s += it.start(); s += it.end() } }; extra?.let { s += it }; return s
    }

    fun run(): List<String> = try {
        val expected = mapOf(
            FaceRegion.LIPS to idx(C.FACE_LANDMARKS_LIPS),
            FaceRegion.LEFT_EYE to idx(C.FACE_LANDMARKS_LEFT_EYE, C.FACE_LANDMARKS_LEFT_IRIS, extra = 473),
            FaceRegion.RIGHT_EYE to idx(C.FACE_LANDMARKS_RIGHT_EYE, C.FACE_LANDMARKS_RIGHT_IRIS, extra = 468),
            FaceRegion.BROWS to idx(C.FACE_LANDMARKS_LEFT_EYE_BROW, C.FACE_LANDMARKS_RIGHT_EYE_BROW),
            FaceRegion.FACE_OVAL to idx(C.FACE_LANDMARKS_FACE_OVAL),
        )
        expected.mapNotNull { (r, e) -> if (r.indices.toSet() != e) "${r.name} differs from MediaPipe" else null }
    } catch (t: Throwable) { listOf("check failed: ${t.message}") }
}
