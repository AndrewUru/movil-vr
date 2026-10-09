package com.muralar.app.marker

import org.junit.Assert.*
import org.junit.Test

class MarkerLayoutTest {
    @Test fun markersSurroundA4WithFiveMillimetreGaps() {
        val sheet = MarkerLayout()
        assertEquals(MarkerLayout.XY(-30.0, -30.0), sheet.markerCorners(0)[0])
        assertEquals(MarkerLayout.XY(-5.0, -5.0), sheet.markerCorners(0)[2])
        assertEquals(MarkerLayout.XY(215.0, -30.0), sheet.markerCorners(1)[0])
        assertEquals(MarkerLayout.XY(215.0, 302.0), sheet.markerCorners(2)[0])
        assertEquals(MarkerLayout.XY(-30.0, 302.0), sheet.markerCorners(3)[0])
    }

    @Test fun changingMuralDimensionsDoesNotScaleThePrintedReference() {
        val mural = MarkerLayout(3000.0, 2000.0, 160.0)
        for (id in 0..3) {
            val corners = mural.markerCorners(id)
            assertEquals(160.0, corners[1].x - corners[0].x, 0.0)
            assertEquals(160.0, corners[2].y - corners[1].y, 0.0)
        }
        assertEquals(MarkerLayout.XY(3005.0, 2005.0), mural.markerCorners(2)[0])
    }

    @Test fun portraitArtworkIsCentredOnLandscapeSurfaceWithoutStretching() {
        val rect = MarkerLayout(1000.0, 500.0, 160.0).imageRect(400, 800)
        assertEquals(MarkerLayout.XY(375.0, 0.0), rect[0])
        assertEquals(MarkerLayout.XY(625.0, 500.0), rect[2])
    }

    @Test fun landscapeArtworkIsCentredOnPortraitSurfaceWithoutStretching() {
        val rect = MarkerLayout(210.0, 297.0).imageRect(1000, 500)
        assertEquals(MarkerLayout.XY(0.0, 96.0), rect[0])
        assertEquals(MarkerLayout.XY(210.0, 201.0), rect[2])
    }

    @Test fun invalidPhysicalMeasurementsAreRejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -100.0, 10001.0).forEach {
            assertThrows(IllegalArgumentException::class.java) { MarkerLayout(width = it) }
            assertThrows(IllegalArgumentException::class.java) { MarkerLayout(height = it) }
        }
        assertThrows(IllegalArgumentException::class.java) { MarkerLayout(markerSize = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { MarkerLayout().markerCorners(4) }
        assertThrows(IllegalArgumentException::class.java) { MarkerLayout().imageRect(0, 10) }
    }
}
