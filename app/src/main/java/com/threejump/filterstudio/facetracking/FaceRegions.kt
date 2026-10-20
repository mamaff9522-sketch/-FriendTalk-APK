package com.threejump.filterstudio.facetracking

/**
 * Face regions = MediaPipe FaceMesh connection index sets (FACE_LANDMARKS_LIPS, LEFT/RIGHT_EYE + IRIS,
 * LEFT/RIGHT_EYE_BROW, FACE_OVAL, and FACEMESH_NOSE), taken from MediaPipe face_mesh_connections.py
 * (Apache-2.0). Kept as plain arrays so they are unit-testable on the JVM (FaceLandmarker's static
 * init loads native code). [MediaPipeRegionCheck] verifies them against the live tasks-vision sets on device.
 * LEFT/RIGHT are the subject's own left/right (MediaPipe convention). Iris centres 473/468 added.
 */
enum class FaceRegion(val thai: String) {
    LIPS("ริมฝีปาก"), LEFT_EYE("ตาซ้าย"), RIGHT_EYE("ตาขวา"), BROWS("คิ้ว"), NOSE("จมูก"), FACE_OVAL("โครงหน้า");

    val indices: IntArray by lazy { FaceRegions.indicesOf(this) }
}

object FaceRegions {
    fun indicesOf(r: FaceRegion): IntArray = when (r) {
        FaceRegion.LIPS -> intArrayOf(0, 13, 14, 17, 37, 39, 40, 61, 78, 80, 81, 82, 84, 87, 88, 91, 95, 146, 178, 181, 185, 191, 267, 269, 270, 291, 308, 310, 311, 312, 314, 317, 318, 321, 324, 375, 402, 405, 409, 415)
        FaceRegion.LEFT_EYE -> intArrayOf(249, 263, 362, 373, 374, 380, 381, 382, 384, 385, 386, 387, 388, 390, 398, 466, 473, 474, 475, 476, 477)
        FaceRegion.RIGHT_EYE -> intArrayOf(7, 33, 133, 144, 145, 153, 154, 155, 157, 158, 159, 160, 161, 163, 173, 246, 468, 469, 470, 471, 472)
        FaceRegion.BROWS -> intArrayOf(46, 52, 53, 55, 63, 65, 66, 70, 105, 107, 276, 282, 283, 285, 293, 295, 296, 300, 334, 336)
        FaceRegion.NOSE -> intArrayOf(1, 2, 4, 5, 6, 19, 45, 48, 64, 94, 97, 98, 115, 168, 195, 197, 220, 275, 278, 294, 326, 327, 344, 440)
        FaceRegion.FACE_OVAL -> intArrayOf(10, 21, 54, 58, 67, 93, 103, 109, 127, 132, 136, 148, 149, 150, 152, 162, 172, 176, 234, 251, 284, 288, 297, 323, 332, 338, 356, 361, 365, 377, 378, 379, 389, 397, 400, 454)
    }
}
