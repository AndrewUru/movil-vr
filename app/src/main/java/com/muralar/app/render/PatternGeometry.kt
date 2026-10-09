package com.muralar.app.render

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** All dimensions are meters in the anchor's local XZ plane. */
object PatternGeometry {
    const val SIZE_METERS = 1f
    const val SURFACE_OFFSET_METERS = 0.002f

    fun uprightAngle(localUpX: Float, localUpZ: Float): Float = atan2(localUpX, localUpZ)

    fun point(x: Float, z: Float, angle: Float): FloatArray {
        val c = cos(angle)
        val s = sin(angle)
        // Viewed from +Y (the wall normal), right is up × normal, not normal × up.
        return floatArrayOf(-c * x + s * z, SURFACE_OFFSET_METERS, s * x + c * z)
    }

    fun outline(angle: Float): FloatArray = points(
        floatArrayOf(-.5f, -.5f, .5f, -.5f, .5f, .5f, -.5f, .5f), angle,
    )

    fun grid(angle: Float): FloatArray {
        val coordinates = ArrayList<Float>()
        for (i in 1..9) {
            val at = -.5f + i / 10f
            coordinates.addAll(listOf(at, -.5f, at, .5f, -.5f, at, .5f, at))
        }
        return points(coordinates.toFloatArray(), angle)
    }

    // A distinct upper-left reference, within the one-meter boundary.
    fun reference(angle: Float): FloatArray = points(
        floatArrayOf(-.5f, .38f, -.5f, .5f, -.38f, .5f), angle,
    )

    private fun points(pairs: FloatArray, angle: Float): FloatArray =
        FloatArray(pairs.size / 2 * 3).also { result ->
            for (i in pairs.indices step 2) {
                point(pairs[i], pairs[i + 1], angle).copyInto(result, i / 2 * 3)
            }
        }
}
