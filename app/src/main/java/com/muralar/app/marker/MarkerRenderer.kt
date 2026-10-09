package com.muralar.app.marker

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect
import kotlin.math.abs
import kotlin.math.hypot

/** A planar, frame-local reference. No last-known transform survives a failed detection. */
class MarkerRenderer {
    data class Status(val markers: Int, val aligned: Boolean)
    private val parameters = DetectorParameters().apply {
        set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)
    }
    private val detector = ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50), parameters)
    private val artwork = Mat()
    private val warped = Mat()
    private val warpedFloat = Mat()
    private val cameraFloat = Mat()
    private val alpha = Mat()
    private val alphaFour = Mat()
    private val inverseAlpha = Mat()
    private val foreground = Mat()
    private val background = Mat()
    private var ones = Mat()
    private var opaque = Mat()
    @Volatile var layout = MarkerLayout()
    @Volatile var opacity = 0.55
    @Volatile var visible = true

    @Synchronized fun setArtwork(bitmap: Bitmap) {
        // Request straight alpha; blend it explicitly after perspective warping.
        Utils.bitmapToMat(bitmap, artwork, true)
    }

    @Synchronized fun render(rgba: Mat, gray: Mat): Status {
        val temporary = mutableListOf<Mat>()
        fun <T : Mat> keep(value: T): T { temporary.add(value); return value }
        val corners = mutableListOf<Mat>()
        var markerCount = 0
        try {
            val ids = keep(Mat())
            detector.detectMarkers(gray, corners, ids)
            val current = layout
            val world = mutableListOf<Point>()
            val screen = mutableListOf<Point>()
            val seen = mutableSetOf<Int>()
            for (i in 0 until ids.rows()) {
                val id = ids.get(i, 0)[0].toInt()
                if (id !in 0..3) continue
                // Two copies of an ID make the physical reference ambiguous.
                if (!seen.add(id)) return Status(seen.size, false)
                val points = (0..3).map { j -> corners[i].get(0, j).let { Point(it[0], it[1]) } }
                if (points.indices.any { j -> distance(points[j], points[(j + 1) % 4]) < 14.0 }) continue
                world.addAll(current.markerCorners(id).map { Point(it.x, it.y) })
                screen.addAll(points)
                markerCount++
            }
            if (markerCount < 3 || artwork.empty()) return Status(markerCount, false)
            val src = keep(MatOfPoint2f(*world.toTypedArray()))
            val dst = keep(MatOfPoint2f(*screen.toTypedArray()))
            val inliers = keep(Mat())
            val homography = keep(Calib3d.findHomography(src, dst, Calib3d.RANSAC, 3.0, inliers))
            if (homography.empty() || Core.countNonZero(inliers) < world.size * 0.85) return Status(markerCount, false)
            // Every visible marker must agree; do not silently accept a misplaced corner.
            for (i in 0 until markerCount) {
                if ((0..3).count { inliers.get(i * 4 + it, 0)[0] != 0.0 } < 3) return Status(markerCount, false)
            }
            val projected = keep(MatOfPoint2f())
            Core.perspectiveTransform(src, projected, homography)
            if (projected.toArray().zip(screen).any { (a, b) -> distance(a, b) > 5.0 }) return Status(markerCount, false)

            val borderWorld = keep(MatOfPoint2f(Point(0.0, 0.0), Point(current.width, 0.0),
                Point(current.width, current.height), Point(0.0, current.height)))
            val border = keep(MatOfPoint2f())
            Core.perspectiveTransform(borderWorld, border, homography)
            val bounds = border.toArray()
            if (bounds.any { !it.x.isFinite() || !it.y.isFinite() || abs(it.x) > rgba.cols() * 3 || abs(it.y) > rgba.rows() * 3 }) {
                return Status(markerCount, false)
            }
            val polygon = keep(MatOfPoint(*bounds))
            if (!Imgproc.isContourConvex(polygon) || Imgproc.contourArea(polygon, true) < 1600.0) return Status(markerCount, false)

            if (visible && opacity > 0) {
                val imageWorld = keep(MatOfPoint2f(*current.imageRect(artwork.cols(), artwork.rows()).map { Point(it.x, it.y) }.toTypedArray()))
                val imageScreen = keep(MatOfPoint2f())
                Core.perspectiveTransform(imageWorld, imageScreen, homography)
                val imagePixels = keep(MatOfPoint2f(Point(0.0, 0.0), Point(artwork.cols() - 1.0, 0.0),
                    Point(artwork.cols() - 1.0, artwork.rows() - 1.0), Point(0.0, artwork.rows() - 1.0)))
                val transform = keep(Imgproc.getPerspectiveTransform(imagePixels, imageScreen))
                Imgproc.warpPerspective(artwork, warped, transform, rgba.size(), Imgproc.INTER_LINEAR,
                    Core.BORDER_CONSTANT, Scalar.all(0.0))
                blend(rgba)
            }
            Imgproc.polylines(rgba, listOf(polygon), true, Scalar(181.0, 245.0, 212.0, 255.0), 2, Imgproc.LINE_AA)
            return Status(markerCount, true)
        } finally {
            corners.forEach { it.release() }
            temporary.forEach { it.release() }
        }
    }

    private fun blend(rgba: Mat) {
        if (ones.size() != rgba.size()) {
            ones.release(); opaque.release()
            ones = Mat(rgba.rows(), rgba.cols(), CvType.CV_32FC4, Scalar.all(1.0))
            opaque = Mat(rgba.rows(), rgba.cols(), CvType.CV_8UC1, Scalar.all(255.0))
        }
        Core.extractChannel(warped, alpha, 3)
        alpha.convertTo(alpha, CvType.CV_32F, opacity / 255.0)
        Core.merge(listOf(alpha, alpha, alpha, alpha), alphaFour)
        Core.subtract(ones, alphaFour, inverseAlpha)
        warped.convertTo(warpedFloat, CvType.CV_32F)
        rgba.convertTo(cameraFloat, CvType.CV_32F)
        Core.multiply(warpedFloat, alphaFour, foreground)
        Core.multiply(cameraFloat, inverseAlpha, background)
        Core.add(foreground, background, cameraFloat)
        cameraFloat.convertTo(rgba, CvType.CV_8U)
        Core.insertChannel(opaque, rgba, 3)
    }

    @Synchronized fun release() {
        listOf(artwork, warped, warpedFloat, cameraFloat, alpha, alphaFour, inverseAlpha,
            foreground, background, ones, opaque).forEach { it.release() }
        detector.clear()
    }

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}
