/*
 * Full-screen quad for the filter pass. Licensed under the Apache License, Version 2.0.
 */
package com.threejump.filterstudio.filterengine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * One attribute only (aPos, vec2 NDC), tightly packed, drawn as GL_TRIANGLE_STRIP from a client-side
 * direct native-order buffer with NO VBO bound (glBindBuffer(GL_ARRAY_BUFFER, 0)), stride 0, offset 0.
 * Texture coordinates are derived in the vertex shader, so there is no interleaving to get wrong.
 */
object QuadGeometry {
    const val COMPONENTS = 2
    const val VERTEX_COUNT = 4
    const val STRIDE_BYTES = 0
    /** bottom-left, bottom-right, top-left, top-right (strip order). */
    val POSITIONS = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

    fun newBuffer(): FloatBuffer = ByteBuffer.allocateDirect(POSITIONS.size * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(POSITIONS); position(0) }
}
