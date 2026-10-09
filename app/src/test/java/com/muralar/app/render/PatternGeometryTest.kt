package com.muralar.app.render

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class PatternGeometryTest {
    @Test fun `one meter dimensions survive local rotation`() {
        for (angle in listOf(0f, .63f, (PI / 2).toFloat(), PI.toFloat())) {
            val points = PatternGeometry.outline(angle).toList().chunked(3)
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % 4]
                val distance = sqrt(a.indices.sumOf { ((a[it] - b[it]) * (a[it] - b[it])).toDouble() })
                assertEquals(1.0, distance, .00001)
                assertEquals(.002f, a[1], .000001f)
            }
        }
    }

    @Test fun `pattern up follows world up projected into anchor plane`() {
        for ((x, z) in listOf(0f to 1f, 1f to 0f, -1f to 0f, 0f to -1f, .6f to .8f)) {
            val top = PatternGeometry.point(0f, 1f, PatternGeometry.uprightAngle(x, z))
            assertEquals(x, top[0], .00001f)
            assertEquals(z, top[2], .00001f)
        }
    }

    @Test fun `grid uses nine internal lines on each axis spaced ten centimeters`() {
        val lines = PatternGeometry.grid(0f).toList().chunked(6)
        assertEquals(18, lines.size)
        for (i in 0..8) {
            assertEquals(.4f - i * .1f, lines[i * 2][0], .00001f)
            assertEquals(-.5f, lines[i * 2][2], .00001f)
            assertEquals(.5f, lines[i * 2][5], .00001f)
        }
    }

    @Test fun `reference corner is upper left viewed from the wall normal`() {
        val corner = PatternGeometry.reference(0f)
        // Camera on +Y, up on +Z: screen-left is local +X.
        assertEquals(.5f, corner[3], .00001f)
        assertEquals(.5f, corner[5], .00001f)
    }
}
