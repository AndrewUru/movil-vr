package com.muralar.app.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotTrackingException
import com.google.ar.core.exceptions.ResourceExhaustedException
import com.muralar.app.render.GlScene
import com.muralar.app.render.PatternGeometry
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/** Session.update, hit tests, anchors, and GL work share one thread. */
class ArRenderer(
    private val status: (ArStatus) -> Unit,
    private val notice: (Boolean) -> Unit,
    private val fatal: (Exception) -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile var session: Session? = null
    @Volatile var displayRotation: Int = 0
    private val pendingTap = AtomicReference<Pair<Float, Float>?>(null)
    private val scene = GlScene()
    private var anchor: Anchor? = null
    private var width = 1
    private var height = 1
    private var failed = false
    private var lastStatus = 0L
    private var lastStateKey = ""
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    fun tap(x: Float, y: Float) { pendingTap.set(x to y) }
    fun clearPendingInput() { pendingTap.set(null) }

    /** Call only via GLSurfaceView.queueEvent, or after its render thread has paused. */
    fun resetPlacement() {
        anchor?.detach()
        anchor = null
        pendingTap.set(null)
        lastStatus = 0
    }

    fun resumeRendering() { failed = false }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try { scene.create() } catch (e: Exception) { fail(e) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val active = session ?: return
        if (failed) return
        try {
            active.setDisplayGeometry(displayRotation, width, height)
            active.setCameraTextureName(scene.cameraTexture)
            val frame = active.update()
            scene.drawCamera(frame)
            val camera = frame.camera
            val cameraTracking = camera.trackingState == TrackingState.TRACKING
            val walls = active.getAllTrackables(Plane::class.java).filter {
                it.type == Plane.Type.VERTICAL && it.trackingState == TrackingState.TRACKING && it.subsumedBy == null
            }
            val tap = pendingTap.getAndSet(null)
            if (tap != null && anchor == null && cameraTracking) place(frame, tap)

            if (cameraTracking) {
                camera.getProjectionMatrix(projection, 0, .05f, 50f)
                camera.getViewMatrix(view, 0)
                Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
                val placed = anchor
                if (placed == null) {
                    walls.forEach { plane ->
                        plane.centerPose.toMatrix(model, 0)
                        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
                        scene.drawPlane(plane, mvp)
                    }
                } else if (placed.trackingState == TrackingState.TRACKING) {
                    // Read the anchor pose every frame. Never replace it with the camera or plane center.
                    placed.pose.toMatrix(model, 0)
                    Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
                    scene.drawPattern(mvp)
                }
            }
            val current = anchor
            val distance = if (cameraTracking && current?.trackingState == TrackingState.TRACKING) {
                val a = current.pose.translation
                val c = camera.pose.translation
                sqrt((a[0]-c[0])*(a[0]-c[0]) + (a[1]-c[1])*(a[1]-c[1]) + (a[2]-c[2])*(a[2]-c[2]))
            } else null
            val snapshot = ArStatus(camera.trackingState, camera.trackingFailureReason,
                walls.size, current?.trackingState, distance)
            val now = SystemClock.elapsedRealtime()
            val key = "${snapshot.camera}/${snapshot.failure}/${snapshot.anchor}/${snapshot.walls > 0}"
            if (key != lastStateKey || now - lastStatus >= 250) {
                lastStatus = now
                lastStateKey = key
                status(snapshot)
            }
        } catch (e: CameraNotAvailableException) { fail(e)
        } catch (e: Exception) { fail(e) }
    }

    private fun place(frame: Frame, tap: Pair<Float, Float>) {
        val hit = frame.hitTest(tap.first, tap.second).firstOrNull { result ->
            val plane = result.trackable as? Plane
            plane != null && plane.type == Plane.Type.VERTICAL &&
                plane.trackingState == TrackingState.TRACKING && plane.subsumedBy == null &&
                plane.isPoseInPolygon(result.hitPose)
        }
        if (hit == null) { notice(false); return }
        try {
            val placed = hit.createAnchor()
            anchor = placed
            val localUp = placed.pose.inverse().rotateVector(floatArrayOf(0f, 1f, 0f))
            scene.orientPattern(PatternGeometry.uprightAngle(localUp[0], localUp[2]))
            Log.i(TAG, "Anchor created on vertical plane. Pattern=1m; distance=${hit.distance}m")
        } catch (e: NotTrackingException) { notice(true)
        } catch (e: ResourceExhaustedException) { notice(true) }
    }

    private fun fail(e: Exception) {
        if (failed) return
        failed = true
        Log.e(TAG, "AR renderer stopped", e)
        fatal(e)
    }

    companion object { private const val TAG = "MuralAR" }
}
