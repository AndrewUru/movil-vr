package com.muralar.app.marker

import kotlin.math.min

/** Millimetres, origin at the drawing's top left; x right, y down. */
data class MarkerLayout(val width: Double = 210.0, val height: Double = 297.0,
                        val markerSize: Double = 25.0) {
    data class XY(val x: Double, val y: Double)
    init {
        require(width.isFinite() && width in 50.0..10000.0)
        require(height.isFinite() && height in 50.0..10000.0)
        require(markerSize.isFinite() && markerSize in 20.0..300.0)
    }

    // All four printed markers face up. There is a 5 mm gap on both axes.
    fun markerCorners(id: Int): List<XY> {
        require(id in 0..3)
        val x = if (id == 0 || id == 3) -markerSize - GAP else width + GAP
        val y = if (id < 2) -markerSize - GAP else height + GAP
        return listOf(XY(x, y), XY(x + markerSize, y),
            XY(x + markerSize, y + markerSize), XY(x, y + markerSize))
    }

    /** Fit inside the physical drawing rectangle, preserving the image aspect ratio. */
    fun imageRect(imageWidth: Int, imageHeight: Int): List<XY> {
        require(imageWidth > 0 && imageHeight > 0)
        val scale = min(width / imageWidth, height / imageHeight)
        val w = imageWidth * scale
        val h = imageHeight * scale
        val x = (width - w) / 2
        val y = (height - h) / 2
        return listOf(XY(x, y), XY(x + w, y), XY(x + w, y + h), XY(x, y + h))
    }

    companion object { const val GAP = 5.0 }
}
