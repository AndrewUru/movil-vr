package com.muralar.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.muralar.app.ar.ArRenderer
import com.muralar.app.ar.ArStatus
import com.muralar.app.ar.ArSurfaceView

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private lateinit var surface: GLSurfaceView
    private lateinit var renderer: ArRenderer
    private lateinit var headline: TextView
    private lateinit var hint: TextView
    private lateinit var diagnostics: TextView
    private lateinit var dimensions: TextView
    private lateinit var action: Button
    private lateinit var privacy: TextView
    private var session: Session? = null
    private var resumed = false
    private var running = false
    private var requestedStart = false
    private var installRequested = false
    private var permissionRequested = false
    private var placed = false
    private val mint = Color.rgb(181, 245, 212)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        renderer = ArRenderer(
            status = { state -> runOnUiThread { if (running) renderStatus(state) } },
            notice = { failed -> runOnUiThread {
                if (running) Toast.makeText(this, if (failed) R.string.placement_failed else R.string.miss, Toast.LENGTH_SHORT).show()
            } },
            fatal = { error -> runOnUiThread {
                if (running) { pauseAr(); showError(errorText(error)) }
            } },
        )
        createUi()
        // GLSurfaceView starts a thread at setRenderer; keep it paused until the user starts AR.
        surface.onPause()
    }

    private fun createUi() {
        val root = FrameLayout(this)
        surface = ArSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            contentDescription = getString(R.string.surface_hint)
            onArTap = { x, y -> if (running && !placed) renderer.tap(x, y) }
        }
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))

        val chrome = FrameLayout(this)
        root.addView(chrome, FrameLayout.LayoutParams(-1, -1))
        chrome.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(dp(20) + insets.systemWindowInsetLeft, dp(16) + insets.systemWindowInsetTop,
                dp(20) + insets.systemWindowInsetRight, dp(16) + insets.systemWindowInsetBottom)
            insets
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = panel()
        }
        top.addView(label(getString(R.string.brand), 20f, mint).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = .12f
        })
        top.addView(label(getString(R.string.phase), 11f, Color.LTGRAY).apply { setPadding(0, dp(7), 0, dp(12)) })
        diagnostics = label("", 12f, Color.LTGRAY).apply { visibility = View.GONE }
        top.addView(diagnostics)
        val about = Button(this).apply {
            text = getString(R.string.about)
            isAllCaps = false
            textSize = 12f
            setTextColor(mint)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { showPrivacy() }
        }
        top.addView(about, LinearLayout.LayoutParams(-1, dp(48)))
        chrome.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(18))
            background = panel()
        }
        dimensions = label(getString(R.string.test_dimensions), 11f, mint)
        bottom.addView(dimensions)
        headline = label(getString(R.string.initial_title), 26f, Color.WHITE).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(12), 0, dp(10))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        hint = label(getString(R.string.initial_hint), 15f, Color.rgb(206, 216, 211)).apply {
            setPadding(0, 0, 0, dp(16))
        }
        bottom.addView(headline)
        bottom.addView(hint)
        action = Button(this).apply {
            text = getString(R.string.start)
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.rgb(15, 34, 24))
            background = GradientDrawable().apply { setColor(mint); cornerRadius = dp(14).toFloat() }
            setOnClickListener { requestedStart = true; startAr() }
        }
        bottom.addView(action, LinearLayout.LayoutParams(-1, dp(56)))
        privacy = label(getString(R.string.privacy), 11f, Color.LTGRAY).apply { setPadding(0, dp(14), 0, 0) }
        bottom.addView(privacy)
        val scroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val availableWidth = chrome.width - chrome.paddingLeft - chrome.paddingRight
                val availableHeight = (chrome.height - chrome.paddingTop - chrome.paddingBottom)
                    .takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
                val maxHeight = if (availableWidth > availableHeight) availableHeight else
                    (availableHeight - top.measuredHeight - dp(12)).coerceAtLeast(dp(120))
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
            }
        }.apply {
            isFillViewport = false
            addView(bottom)
        }
        chrome.addView(scroll, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        // Keep instructions accessible on landscape displays and with large system fonts.
        chrome.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val availableWidth = chrome.width - chrome.paddingLeft - chrome.paddingRight
            val availableHeight = chrome.height - chrome.paddingTop - chrome.paddingBottom
            val landscape = availableWidth > availableHeight
            val cardWidth = if (landscape) (availableWidth - dp(16)) / 2 else availableWidth
            if (cardWidth > 0 && availableHeight > 0) {
                val topParams = top.layoutParams as FrameLayout.LayoutParams
                if (topParams.width != cardWidth) {
                    topParams.width = cardWidth
                    top.layoutParams = topParams
                }
                val params = scroll.layoutParams as FrameLayout.LayoutParams
                val desiredGravity = Gravity.BOTTOM or if (landscape) Gravity.END else Gravity.START
                if (params.width != cardWidth || params.gravity != desiredGravity) {
                    params.width = cardWidth
                    params.gravity = desiredGravity
                    scroll.layoutParams = params
                }
            }
        }
        setContentView(root)
        root.requestApplyInsets()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        getSystemService(DisplayManager::class.java).registerDisplayListener(this, null)
        updateDisplayRotation()
        if (requestedStart) startAr()
    }

    override fun onPause() {
        resumed = false
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(this)
        pauseAr()
        super.onPause()
    }

    private fun pauseAr() {
        running = false
        renderer.clearPendingInput()
        // Stop update() before pausing the Session (avoids SessionPausedException races).
        surface.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        // onPause has already joined the GL thread; no AR objects are in use now.
        renderer.resetPlacement()
        renderer.session = null
        session?.close()
        session = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateDisplayRotation()
    }

    @Suppress("DEPRECATION")
    private fun updateDisplayRotation() { renderer.displayRotation = windowManager.defaultDisplay.rotation }

    override fun onDisplayChanged(displayId: Int) { updateDisplayRotation() }
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit

    private fun startAr() {
        if (!resumed || running) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!permissionRequested) {
                permissionRequested = true
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
            } else showPermissionError()
            return
        }
        try {
            if (session == null) {
                if (ArCoreApk.getInstance().requestInstall(this, !installRequested) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested = true
                    return
                }
                val created = Session(this)
                try {
                    created.configure(Config(created).apply {
                        planeFindingMode = Config.PlaneFindingMode.VERTICAL
                        focusMode = Config.FocusMode.AUTO
                        instantPlacementMode = Config.InstantPlacementMode.DISABLED
                        depthMode = Config.DepthMode.DISABLED
                        lightEstimationMode = Config.LightEstimationMode.DISABLED
                    })
                } catch (e: Exception) { created.close(); throw e }
                session = created
                renderer.session = created
            }
            session!!.resume()
            renderer.resumeRendering()
            running = true
            headline.setText(R.string.search_title)
            hint.setText(R.string.search_hint)
            action.visibility = View.GONE
            privacy.visibility = View.GONE
            diagnostics.visibility = View.VISIBLE
            surface.onResume()
        } catch (e: Exception) {
            Log.e("MuralAR", "Unable to start AR", e)
            showError(errorText(e))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startAr()
            else showPermissionError()
        }
    }

    private fun showPermissionError() {
        val canAsk = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        headline.setText(R.string.error_title)
        hint.setText(if (canAsk) R.string.camera_permission else R.string.camera_settings)
        action.visibility = View.VISIBLE
        action.setText(if (canAsk) R.string.retry else R.string.settings)
        action.setOnClickListener {
            if (canAsk) { permissionRequested = false; startAr() }
            else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun showError(message: Int) {
        headline.setText(R.string.error_title)
        hint.setText(message)
        diagnostics.visibility = View.GONE
        action.visibility = View.VISIBLE
        action.setText(R.string.retry)
        action.setOnClickListener {
            // A fatal renderer/session error starts an explicitly new session, never silently re-anchors.
            renderer.resetPlacement()
            renderer.session = null
            session?.close()
            session = null
            placed = false
            installRequested = false
            startAr()
        }
    }

    private fun renderStatus(state: ArStatus) {
        placed = state.anchor != null
        val cameraOk = state.camera == TrackingState.TRACKING
        val anchorOk = state.anchor == TrackingState.TRACKING
        diagnostics.text = getString(R.string.diagnostics, state.walls, state.camera.name, state.anchor?.name ?: "—")
        dimensions.text = state.distanceMeters?.let { getString(R.string.distance, it) }
            ?: getString(R.string.test_dimensions)
        when {
            !cameraOk -> {
                headline.setText(R.string.limited_title)
                hint.setText(when (state.failure) {
                    TrackingFailureReason.INSUFFICIENT_LIGHT -> R.string.insufficient_light
                    TrackingFailureReason.INSUFFICIENT_FEATURES -> R.string.insufficient_features
                    TrackingFailureReason.EXCESSIVE_MOTION -> R.string.excessive_motion
                    TrackingFailureReason.BAD_STATE -> R.string.bad_state
                    TrackingFailureReason.CAMERA_UNAVAILABLE -> R.string.camera_unavailable
                    else -> R.string.initializing
                })
            }
            placed && !anchorOk -> {
                headline.setText(R.string.limited_title)
                hint.setText(if (state.anchor == TrackingState.STOPPED) R.string.anchor_lost else R.string.anchor_paused)
            }
            placed -> { headline.setText(R.string.anchored_title); hint.setText(R.string.anchored_hint) }
            state.walls > 0 -> { headline.setText(R.string.surface_title); hint.setText(R.string.surface_hint) }
            else -> { headline.setText(R.string.search_title); hint.setText(R.string.search_hint) }
        }
        headline.setTextColor(if (!cameraOk || (placed && !anchorOk)) Color.rgb(255, 191, 126) else Color.WHITE)
        action.visibility = if (placed) View.VISIBLE else View.GONE
        action.setText(R.string.reset)
        action.setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.reset_title).setMessage(R.string.reset_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reset) { _, _ ->
                    surface.queueEvent { renderer.resetPlacement() }
                }.show()
        }
    }

    private fun showPrivacy() {
        AlertDialog.Builder(this).setTitle(R.string.about).setMessage(R.string.privacy)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.privacy_link) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://support.google.com/ar/answer/12148145?hl=es")))
            }.show()
    }

    private fun errorText(e: Exception) = when (e) {
        is UnavailableDeviceNotCompatibleException -> R.string.unsupported
        is UnavailableArcoreNotInstalledException, is UnavailableUserDeclinedInstallationException,
        is UnavailableApkTooOldException -> R.string.install_ar
        is UnavailableSdkTooOldException -> R.string.update_app
        is CameraNotAvailableException -> R.string.camera_unavailable
        is SecurityException -> R.string.camera_permission
        else -> R.string.unknown_error
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
    }

    private fun panel() = GradientDrawable().apply {
        setColor(Color.argb(230, 16, 26, 22)); cornerRadius = dp(22).toFloat()
        setStroke(dp(1), Color.argb(45, 181, 245, 212))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { private const val CAMERA_REQUEST = 10 }
}
