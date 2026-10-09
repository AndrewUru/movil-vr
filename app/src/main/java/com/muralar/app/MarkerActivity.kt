package com.muralar.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.muralar.app.marker.MarkerLayout
import com.muralar.app.marker.MarkerRenderer
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import java.util.concurrent.Executors
import kotlin.math.max

/** Ordinary rear camera + printed planar reference; never starts an ARCore session. */
class MarkerActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2 {
    private lateinit var camera: JavaCameraView
    private lateinit var renderer: MarkerRenderer
    private lateinit var status: TextView
    private lateinit var dimensions: TextView
    private lateinit var cameraButton: Button
    private val files = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("marker-drawing", MODE_PRIVATE) }
    private var requested = false
    private var resumed = false
    private var ready = false
    private var destroyed = false
    private var lastStatus: MarkerRenderer.Status? = null
    private var lastStatusTime = 0L
    private val mint = Color.rgb(181, 245, 212)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initLocal()) {
            AlertDialog.Builder(this).setTitle("No se pudo iniciar la cámara")
                .setMessage("No se pudo cargar el motor de seguimiento. Reinstala la APK completa.")
                .setPositiveButton("Cerrar") { _, _ -> finish() }.setCancelable(false).show()
            return
        }
        renderer = MarkerRenderer()
        renderer.layout = runCatching {
            MarkerLayout(prefs.getString("width", "210")!!.toDouble(),
                prefs.getString("height", "297")!!.toDouble(), prefs.getString("marker", "25")!!.toDouble())
        }.getOrDefault(MarkerLayout())
        renderer.opacity = prefs.getFloat("opacity", .55f).toDouble().coerceIn(0.0, 1.0)
        if (prefs.getString("image", null) == null) {
            val sample = sampleArtwork()
            renderer.setArtwork(sample)
            sample.recycle()
        }
        renderer.visible = savedInstanceState?.getBoolean("visible") ?: true
        requested = savedInstanceState?.getBoolean("requested") ?: false
        createUi()
        ready = true
        prefs.getString("image", null)?.let { loadImage(Uri.parse(it)) }
    }

    private fun createUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 24, 22))
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(12), dp(6)) }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(label("DIBUJO / CÁMARA", 18f, mint))
        dimensions = label("", 12f)
        labels.addView(dimensions)
        heading.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        cameraButton = button("Abrir cámara") { requestCamera() }
        heading.addView(cameraButton)
        root.addView(heading)
        status = label("Coloca los cuatro marcadores alrededor de la zona de dibujo. Consulta «Guía».", 13f).apply {
            setPadding(dp(16), 0, dp(16), dp(8)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(status)
        camera = JavaCameraView(this, CameraBridgeViewBase.CAMERA_ID_BACK).apply {
            setMaxFrameSize(960, 720)
            setCvCameraViewListener(this@MarkerActivity)
            contentDescription = "Cámara con el dibujo alineado a los marcadores"
        }
        root.addView(camera, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(3), dp(10), dp(6)) }
        val actions = LinearLayout(this)
        actions.addView(button("Imagen") { chooseImage() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(button("Formato") { formatDialog() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(button("Guía") { showGuide() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(actions)
        val blend = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val amount = label("Opacidad", 12f)
        blend.addView(amount)
        blend.addView(SeekBar(this).apply {
            max = 100; progress = (renderer.opacity * 100).toInt()
            contentDescription = "Opacidad del dibujo"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) { renderer.opacity = value / 100.0 }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) { prefs.edit().putFloat("opacity", renderer.opacity.toFloat()).apply() }
            })
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        blend.addView(button(if (renderer.visible) "Ocultar" else "Mostrar") {
            renderer.visible = !renderer.visible
            (it as Button).text = if (renderer.visible) "Ocultar" else "Mostrar"
        }, LinearLayout.LayoutParams(-2, dp(48)))
        controls.addView(blend)
        root.addView(controls)
        setContentView(root)
        root.requestApplyInsets()
        updateDimensions()
    }

    private fun requestCamera() {
        requested = true
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else if (prefs.getBoolean("permissionRequested", false) && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this).setTitle("Permiso de cámara")
                .setMessage("Activa el permiso de cámara en los ajustes de la aplicación.")
                .setPositiveButton("Abrir ajustes") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                .setNegativeButton("Cancelar", null).show()
        } else {
            prefs.edit().putBoolean("permissionRequested", true).apply()
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA)
        }
    }

    private fun startCamera() {
        if (!ready || !resumed || !requested || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        camera.disableView()
        status.setText(R.string.marker_searching)
        camera.setCameraPermissionGranted()
        camera.enableView()
        cameraButton.setText(R.string.retry)
    }

    override fun onResume() { super.onResume(); resumed = true; startCamera() }
    override fun onPause() {
        resumed = false
        if (ready) camera.disableView()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }
    override fun onDestroy() {
        destroyed = true
        if (ready) { camera.disableView(); renderer.release() }
        files.shutdownNow()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requested", requested)
        if (ready) outState.putBoolean("visible", renderer.visible)
        super.onSaveInstanceState(outState)
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == CAMERA && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
        else if (code == CAMERA) status.setText(R.string.marker_permission)
    }

    override fun onCameraViewStarted(width: Int, height: Int) { lastStatus = null; lastStatusTime = 0 }
    override fun onCameraViewStopped() {}
    override fun onCameraFrame(frame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val rgba = frame.rgba()
        try {
            val state = renderer.render(rgba, frame.gray())
            val now = android.os.SystemClock.elapsedRealtime()
            if (state != lastStatus && now - lastStatusTime > 400) {
                lastStatus = state; lastStatusTime = now
                runOnUiThread {
                    if (!destroyed && resumed) {
                        status.text = getString(if (state.aligned) R.string.marker_visible else R.string.marker_hidden, state.markers)
                        status.setTextColor(if (state.aligned) mint else Color.WHITE)
                    }
                }
            }
        } catch (error: org.opencv.core.CvException) {
            android.util.Log.e("MarkerCamera", "Frame processing failed", error)
            // Stop after a processing error instead of repeatedly showing an unreliable overlay.
            runOnUiThread {
                if (!destroyed && resumed) {
                    camera.disableView()
                    status.setText(R.string.marker_error)
                }
            }
        }
        return rgba
    }

    private fun chooseImage() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == IMAGE) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            loadImage(uri)
        } else if (requestCode == PDF) {
            files.execute {
                val result = runCatching {
                    contentResolver.openOutputStream(uri)?.use { output -> assets.open("marcadores-dibujo.pdf").use { it.copyTo(output) } }
                        ?: error("No se pudo abrir el destino")
                }
                runOnUiThread { if (!destroyed) toast(if (result.isSuccess) "Plantillas guardadas. Imprime al 100 %." else "No se pudo guardar el PDF.") }
            }
        }
    }

    private fun loadImage(uri: Uri) {
        files.execute {
            val result = runCatching { decodeImage(uri) }
            runOnUiThread {
                val bitmap = result.getOrNull()
                if (destroyed) { bitmap?.recycle(); return@runOnUiThread }
                if (bitmap == null) toast("No se pudo abrir la imagen. Prueba un archivo PNG o JPG.")
                else {
                    renderer.setArtwork(bitmap); bitmap.recycle()
                    prefs.edit().putString("image", uri.toString()).apply()
                    toast("Imagen cargada. Se ajusta al formato sin deformarla.")
                }
            }
        }
    }

    private fun decodeImage(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 1 && bounds.outHeight > 1)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) } ?: error("Invalid image")
        val orientation = runCatching { contentResolver.openInputStream(uri)!!.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun formatDialog() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(10), dp(22), dp(10)) }
        fun field(title: String, initial: Double): EditText {
            content.addView(label(title, 13f, Color.LTGRAY))
            return EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(number(initial)); contentDescription = title
                content.addView(this)
            }
        }
        val current = renderer.layout
        val width = field("Ancho del dibujo (cm)", current.width / 10)
        val height = field("Alto del dibujo (cm)", current.height / 10)
        val marker = field("Lado negro del marcador (cm)", current.markerSize / 10)
        content.addView(button("Elegir tamaño habitual") {
            val presets = arrayOf("A4 · 21 × 29,7 cm", "A3 · 29,7 × 42 cm", "Lienzo · 50 × 70 cm", "Grafiti · 100 × 100 cm", "Intercambiar ancho y alto")
            AlertDialog.Builder(this).setItems(presets) { _, index ->
                if (index == 4) { val old = width.text.toString(); width.setText(height.text.toString()); height.setText(old) }
                else {
                    val values = listOf(Triple(21.0, 29.7, 2.5), Triple(29.7, 42.0, 2.5), Triple(50.0, 70.0, 2.5), Triple(100.0, 100.0, 16.0))[index]
                    width.setText(number(values.first)); height.setText(number(values.second)); marker.setText(number(values.third))
                }
            }.show()
        })
        content.addView(label("Mide la zona real y el lado negro impreso. Para paredes grandes usa las plantillas de 16 cm. Todos los marcadores deben estar en el mismo plano.", 13f, Color.LTGRAY))
        val dialog = AlertDialog.Builder(this).setTitle("Formato y medidas")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("Aplicar", null).setNegativeButton("Cancelar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updated = runCatching { MarkerLayout(
                    width.text.toString().replace(',', '.').toDouble() * 10,
                    height.text.toString().replace(',', '.').toDouble() * 10,
                    marker.text.toString().replace(',', '.').toDouble() * 10) }.getOrNull()
                if (updated == null) toast("Ancho y alto: 5–1000 cm. Marcador: 2–30 cm.")
                else {
                    renderer.layout = updated
                    prefs.edit().putString("width", updated.width.toString()).putString("height", updated.height.toString())
                        .putString("marker", updated.markerSize.toString()).apply()
                    updateDimensions(); dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showGuide() {
        val text = "1. Guarda e imprime las plantillas al 100 %. Usa los cuatro marcadores pequeños para papel o lienzo; los grandes para pared. Recórtalos conservando un margen blanco.\n\n" +
            "2. Delimita el rectángulo donde irá el dibujo. Coloca los marcadores por fuera: 0 arriba izquierda, 1 arriba derecha, 2 abajo derecha, 3 abajo izquierda. Todas las flechas hacia arriba. Deja 5 mm entre el borde negro y cada uno de los dos bordes de su esquina.\n\n" +
            "3. En «Formato», introduce el ancho y alto reales del rectángulo y el lado negro del marcador. La imagen se centra sin deformarse.\n\n" +
            "4. Abre la cámara y encuadra al menos tres marcadores. La imagen solo se ve en la pantalla: el móvil no proyecta luz sobre el papel o la pared. Un soporte facilita dibujar con las manos libres.\n\n" +
            "No tapes ni pintes los marcadores. La superficie debe ser plana. Si te acercas y pierdes las referencias, el dibujo se oculta. Para un mural grande, trabaja por secciones si no puedes encuadrarlas.\n\n" +
            "Comprueba la alineación con trazos de prueba. No se garantiza precisión milimétrica; la lente, la luz y la colocación de los marcadores influyen. Todo se procesa en el móvil."
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12))
            addView(PlacementGuide(this@MarkerActivity), LinearLayout.LayoutParams(-1, dp(190)))
            addView(label(text, 15f, Color.LTGRAY))
        }
        AlertDialog.Builder(this).setTitle("De una hoja a una pared")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("Guardar plantillas") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, "marcadores-dibujo.pdf")
                }, PDF)
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun sampleArtwork(): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mint; strokeWidth = 4f; style = Paint.Style.STROKE }
        canvas.drawRect(20f, 20f, 580f, 580f, paint)
        canvas.drawCircle(300f, 300f, 200f, paint)
        canvas.drawLine(20f, 300f, 580f, 300f, paint)
        canvas.drawLine(300f, 20f, 300f, 580f, paint)
        paint.color = Color.rgb(255, 160, 80)
        canvas.drawRect(20f, 20f, 90f, 90f, paint)
        return bitmap
    }
    private fun updateDimensions() {
        val l = renderer.layout
        dimensions.text = getString(R.string.marker_dimensions, number(l.width / 10), number(l.height / 10), number(l.markerSize / 10))
    }
    private fun number(value: Double) = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString().replace('.', ',')
    private fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun button(value: String, action: (View) -> Unit) = Button(this).apply { text = value; isAllCaps = false; setOnClickListener(action) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    companion object { private const val CAMERA = 10; private const val IMAGE = 11; private const val PDF = 12 }
}

private class PlacementGuide(context: android.content.Context) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markers = listOf(Triple("0", .13f, .12f), Triple("1", .87f, .12f), Triple("2", .87f, .87f), Triple("3", .13f, .87f))
    init {
        setBackgroundColor(Color.WHITE)
        contentDescription = "Marcadores fuera de las esquinas: 0 arriba izquierda, 1 arriba derecha, 2 abajo derecha, 3 abajo izquierda."
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        ink.color = Color.rgb(225, 235, 230); ink.style = Paint.Style.FILL
        canvas.drawRect(w * .23f, h * .22f, w * .77f, h * .77f, ink)
        ink.color = Color.DKGRAY; ink.textSize = h * .08f; ink.textAlign = Paint.Align.CENTER
        canvas.drawText("Zona de dibujo", w / 2, h * .51f, ink)
        markers.forEach { (id, x, y) ->
            ink.color = Color.BLACK
            val size = h * .075f
            canvas.drawRect(w * x - size, h * y - size, w * x + size, h * y + size, ink)
            ink.color = Color.WHITE
            canvas.drawText(id, w * x, h * y + h * .03f, ink)
        }
    }
}
