package com.chaoscamera.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min

class MainActivity : android.app.Activity() {
    private lateinit var cameraManager: CameraManager
    private lateinit var textureView: TextureView
    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var brightnessSeek: SeekBar
    private lateinit var energySpinner: Spinner

    private var cameraId: String? = null
    private var characteristics: CameraCharacteristics? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var running = false
    private var photoCount = 0
    private var pendingShot: Runnable? = null
    private var lastPreviewSurface: Surface? = null

    private lateinit var intervalMin: EditText
    private lateinit var intervalMax: EditText
    private lateinit var isoMin: EditText
    private lateinit var isoMax: EditText
    private lateinit var exposureMin: EditText
    private lateinit var exposureMax: EditText
    private lateinit var focusMin: EditText
    private lateinit var focusMax: EditText
    private lateinit var randomIso: CheckBox
    private lateinit var randomExposure: CheckBox
    private lateinit var randomFocus: CheckBox
    private lateinit var randomInterval: CheckBox
    private lateinit var capabilityText: TextView
    private var maxIso = 3200
    private var minIso = 50
    private var minExposureNs = 100_000L
    private var maxExposureNs = 1_000_000_000L
    private var maxFocusDiopters = 0f
    private var manualSensor = false
    private val secureRandom = SecureRandom()

    companion object { private const val CAMERA_PERMISSION = 41 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        buildUi()
        startBackgroundThread()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(16,19,24)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 12, 18, 24) }
        val title = text("CHAOS CAMERA", 25, Color.WHITE, true)
        content.addView(title)
        content.addView(text("СЛУЧАЙНАЯ ФОТОКАМЕРА · 0.1", 11, Color.LTGRAY, false))

        textureView = TextureView(this).apply { setBackgroundColor(Color.rgb(34,40,49)) }
        content.addView(textureView, LinearLayout.LayoutParams(-1, dp(270)).apply { topMargin = dp(14); bottomMargin = dp(10) })
        statusText = text("КАМЕРА: ОЖИДАНИЕ", 13, Color.rgb(183,243,107), true)
        content.addView(statusText)
        countText = text("Снимков: 0", 13, Color.WHITE, false)
        content.addView(countText)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)) }
        startButton = Button(this).apply { text = "ЗАПУСТИТЬ"; setOnClickListener { startChaos() } }
        stopButton = Button(this).apply { text = "ОСТАНОВИТЬ"; isEnabled = false; setOnClickListener { stopChaos() } }
        buttons.addView(startButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(6) })
        buttons.addView(stopButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        content.addView(buttons)

        section(content, "СЛУЧАЙНОСТЬ")
        randomInterval = CheckBox(this).apply { text = "Случайный интервал между снимками"; isChecked = true; setTextColor(Color.WHITE) }
        content.addView(randomInterval)
        intervalMin = fieldRow(content, "Интервал от (сек.)", "3")
        intervalMax = fieldRow(content, "Интервал до (сек.)", "600")

        randomIso = CheckBox(this).apply { text = "Рандомизировать ISO"; isChecked = true; setTextColor(Color.WHITE) }
        content.addView(randomIso)
        isoMin = fieldRow(content, "ISO от", "100")
        isoMax = fieldRow(content, "ISO до", "1600")

        randomExposure = CheckBox(this).apply { text = "Рандомизировать выдержку"; isChecked = true; setTextColor(Color.WHITE) }
        content.addView(randomExposure)
        exposureMin = fieldRow(content, "Выдержка от (сек.)", "0.00025")
        exposureMax = fieldRow(content, "Выдержка до (сек.)", "0.1")

        randomFocus = CheckBox(this).apply { text = "Рандомизировать фокус"; isChecked = true; setTextColor(Color.WHITE) }
        content.addView(randomFocus)
        focusMin = fieldRow(content, "Фокус от (диоптрии)", "0")
        focusMax = fieldRow(content, "Фокус до (диоптрии)", "5")

        section(content, "ЭКРАН И ЭНЕРГОСБЕРЕЖЕНИЕ")
        content.addView(text("Яркость экрана", 13, Color.LTGRAY, false))
        brightnessSeek = SeekBar(this).apply { max = 100; progress = 5; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { applyBrightness(max(1, progress)) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }) }
        content.addView(brightnessSeek)
        energySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Обычный режим", "Экономичный режим", "Экстремальный режим"))
            onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    when(position) { 0 -> { brightnessSeek.progress = 55 }; 1 -> { brightnessSeek.progress = 15 }; 2 -> { brightnessSeek.progress = 1 } }
                }
            }
        }
        content.addView(energySpinner)
        capabilityText = text("Возможности камеры будут определены при запуске.", 12, Color.LTGRAY, false)
        content.addView(capabilityText)
        content.addView(text("Примечание: если устройство не поддерживает ручную экспозицию, ISO и выдержка останутся автоматическими. Доступность фокусировки также зависит от объектива.", 11, Color.GRAY, false))

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
        textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { openCamera() }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { configureSession() }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { closeCamera(); return true }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }
    private fun section(parent: LinearLayout, label: String) {
        val v = text(label, 14, Color.rgb(183,243,107), true); v.setPadding(0, dp(18), 0, dp(5)); parent.addView(v)
    }
    private fun fieldRow(parent: LinearLayout, label: String, initial: String): EditText {
        parent.addView(text(label, 12, Color.LTGRAY, false))
        return EditText(this).apply {
            setSingleLine(true); setText(initial); textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setPadding(dp(10), dp(3), dp(10), dp(3)); setBackgroundColor(Color.rgb(37,45,56)); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(5) })
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun startBackgroundThread() { backgroundThread = HandlerThread("ChaosCameraThread").also { it.start(); cameraHandler = Handler(it.looper) } }
    private fun stopBackgroundThread() { backgroundThread?.quitSafely(); try { backgroundThread?.join() } catch (_: InterruptedException) {}; backgroundThread = null; cameraHandler = null }

    private fun openCamera() {
        if (!textureView.isAvailable || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id -> cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: cameraManager.cameraIdList.firstOrNull()
            val id = cameraId ?: return
            characteristics = cameraManager.getCameraCharacteristics(id)
            val c = characteristics!!
            val isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            if (isoRange != null) { minIso = isoRange.lower; maxIso = isoRange.upper }
            val expRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            if (expRange != null) { minExposureNs = expRange.lower; maxExposureNs = expRange.upper }
            maxFocusDiopters = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            manualSensor = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            isoMin.setText(max(isoMin.text.toString().toIntOrNull() ?: 100, minIso).toString())
            isoMax.setText(min(isoMax.text.toString().toIntOrNull() ?: 1600, maxIso).coerceAtLeast(minIso).toString())
            focusMax.setText(maxFocusDiopters.toString())
            capabilityText.text = "Камера: ISO $minIso–$maxIso; выдержка ${(minExposureNs/1_000_000.0)}–${(maxExposureNs/1_000_000.0)} мс; ручная экспозиция: ${if(manualSensor) "да" else "нет"}; ручной фокус: ${if(maxFocusDiopters > 0f) "да" else "нет"}."
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
            val sizes = map.getOutputSizes(SurfaceTexture::class.java)
            val size = sizes?.maxByOrNull { it.width.toLong() * it.height } ?: return
            textureView.surfaceTexture?.setDefaultBufferSize(size.width, size.height)
            cameraManager.openCamera(id, object: CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) { device = camera; configureSession() }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); device = null }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); device = null; statusText.text = "ОШИБКА КАМЕРЫ: $error" }
            }, cameraHandler)
        } catch (e: Exception) { statusText.text = "Не удалось открыть камеру: ${e.message}" }
    }

    private fun configureSession() {
        val cam = device ?: return
        val texture = textureView.surfaceTexture ?: return
        try {
            val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
            val jpeg = map.getOutputSizes(android.graphics.ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height } ?: return
            imageReader?.close()
            imageReader = ImageReader.newInstance(jpeg.width, jpeg.height, android.graphics.ImageFormat.JPEG, 2).also { reader ->
                reader.setOnImageAvailableListener({ r ->
                    val image = r.acquireNextImage() ?: return@setOnImageAvailableListener
                    try { saveJpeg(image.planes[0].buffer) } finally { image.close() }
                }, cameraHandler)
            }
            val previewSize = map.getOutputSizes(SurfaceTexture::class.java)?.maxByOrNull { it.width.toLong() * it.height } ?: return
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(texture)
            lastPreviewSurface?.release(); lastPreviewSurface = previewSurface
            previewBuilder = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            cam.createCaptureSession(listOf(previewSurface, imageReader!!.surface), object: CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) { session = s; try { s.setRepeatingRequest(previewBuilder!!.build(), null, cameraHandler); statusText.text = "КАМЕРА АКТИВНА · ОЖИДАНИЕ" } catch (e: Exception) { statusText.text = "Ошибка предпросмотра: ${e.message}" } }
                override fun onConfigureFailed(s: CameraCaptureSession) { statusText.text = "Не удалось настроить камеру" }
            }, cameraHandler)
        } catch (e: Exception) { statusText.text = "Ошибка настройки: ${e.message}" }
    }

    private fun startChaos() {
        if (device == null || session == null) { statusText.text = "Камера ещё не готова"; return }
        if (!validSettings()) return
        running = true; startButton.isEnabled = false; stopButton.isEnabled = true
        statusText.text = "CHAOS АКТИВЕН · ОЖИДАНИЕ СНИМКА"
        scheduleNextShot()
    }
    private fun stopChaos() {
        running = false; pendingShot?.let { mainHandler.removeCallbacks(it) }; pendingShot = null
        startButton.isEnabled = true; stopButton.isEnabled = false
        statusText.text = "ОСТАНОВЛЕНО · Снимков: $photoCount"
    }
    private fun validSettings(): Boolean {
        val imin = intervalMin.text.toString().toDoubleOrNull(); val imax = intervalMax.text.toString().toDoubleOrNull()
        if (imin == null || imax == null || imin <= 0 || imax < imin) { toast("Проверьте диапазон интервала"); return false }
        val a = isoMin.text.toString().toIntOrNull(); val b = isoMax.text.toString().toIntOrNull()
        if (a == null || b == null || a <= 0 || b < a) { toast("Проверьте диапазон ISO"); return false }
        val ea = exposureMin.text.toString().toDoubleOrNull(); val eb = exposureMax.text.toString().toDoubleOrNull()
        if (ea == null || eb == null || ea <= 0 || eb < ea) { toast("Проверьте диапазон выдержки"); return false }
        val fa = focusMin.text.toString().toFloatOrNull(); val fb = focusMax.text.toString().toFloatOrNull()
        if (fa == null || fb == null || fa < 0 || fb < fa) { toast("Проверьте диапазон фокуса"); return false }
        return true
    }
    private fun scheduleNextShot() {
        if (!running) return
        val minS = intervalMin.text.toString().toDoubleOrNull() ?: 3.0
        val maxS = intervalMax.text.toString().toDoubleOrNull() ?: 600.0
        val delay = if (randomInterval.isChecked) randomDouble(minS, maxS) else minS
        statusText.text = "CHAOS АКТИВЕН · СЛЕДУЮЩИЙ СНИМОК НЕИЗВЕСТЕН"
        pendingShot = Runnable { if (running) takeRandomPhoto() }
        mainHandler.postDelayed(pendingShot!!, (delay * 1000).toLong().coerceAtLeast(500))
    }
    private fun takeRandomPhoto() {
        val cam = device; val s = session; val reader = imageReader
        if (!running || cam == null || s == null || reader == null) return
        try {
            val request = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.JPEG_ORIENTATION, (windowManager.defaultDisplay.rotation.let { r -> when(r) { Surface.ROTATION_90 -> 0; Surface.ROTATION_180 -> 270; Surface.ROTATION_270 -> 180; else -> 90 } }))
                val useManual = manualSensor && (randomIso.isChecked || randomExposure.isChecked)
                if (useManual) {
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    val isoLow = (isoMin.text.toString().toIntOrNull() ?: minIso).coerceIn(minIso, maxIso)
                    val isoHigh = (isoMax.text.toString().toIntOrNull() ?: maxIso).coerceIn(isoLow, maxIso)
                    val expLow = ((exposureMin.text.toString().toDoubleOrNull() ?: 0.00025) * 1e9).toLong().coerceIn(minExposureNs, maxExposureNs)
                    val expHigh = ((exposureMax.text.toString().toDoubleOrNull() ?: 0.1) * 1e9).toLong().coerceIn(expLow, maxExposureNs)
                    set(CaptureRequest.SENSOR_SENSITIVITY, if (randomIso.isChecked) randomInt(isoLow, isoHigh) else ((isoLow + isoHigh) / 2))
                    set(CaptureRequest.SENSOR_EXPOSURE_TIME, if (randomExposure.isChecked) randomLong(expLow, expHigh) else ((expLow + expHigh) / 2))
                } else {
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
                if (randomFocus.isChecked && maxFocusDiopters > 0f) {
                    val low = (focusMin.text.toString().toFloatOrNull() ?: 0f).coerceIn(0f, maxFocusDiopters)
                    val high = (focusMax.text.toString().toFloatOrNull() ?: maxFocusDiopters).coerceIn(low, maxFocusDiopters)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    set(CaptureRequest.LENS_FOCUS_DISTANCE, randomDouble(low.toDouble(), high.toDouble()).toFloat())
                } else {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
            }
            s.capture(request.build(), object: CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    runOnUiThread { photoCount++; countText.text = "Снимков: $photoCount"; statusText.text = "СНИМОК $photoCount СОХРАНЯЕТСЯ" }
                    if (running) scheduleNextShot()
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    runOnUiThread { statusText.text = "Сбой съёмки; повторяем цикл" }
                    if (running) scheduleNextShot()
                }
            }, cameraHandler)
        } catch (e: Exception) { statusText.text = "Ошибка снимка: ${e.message}"; if (running) scheduleNextShot() }
    }

    private fun saveJpeg(buffer: java.nio.ByteBuffer) {
        try {
            val name = "CHAOS_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CHAOS CAMERA")
            }
            val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) contentResolver.openOutputStream(uri)?.use { out ->
                val bytes = ByteArray(buffer.remaining()); buffer.get(bytes); out.write(bytes)
            }
        } catch (e: Exception) { runOnUiThread { statusText.text = "Ошибка сохранения: ${e.message}" } }
    }

    private fun applyBrightness(percent: Int) {
        val lp = window.attributes
        lp.screenBrightness = (percent.coerceIn(1, 100) / 100f).coerceIn(0.01f, 1f)
        window.attributes = lp
    }
    private fun randomDouble(a: Double, b: Double): Double = if (b <= a) a else a + secureRandom.nextDouble() * (b - a)
    private fun randomInt(a: Int, b: Int): Int = if (b <= a) a else a + secureRandom.nextInt(b - a + 1)
    private fun randomLong(a: Long, b: Long): Long = if (b <= a) a else a + Math.floorMod(secureRandom.nextLong(), b - a + 1)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else statusText.text = "Для работы CHAOS CAMERA необходимо разрешение камеры"
    }
    override fun onResume() { super.onResume(); if (::textureView.isInitialized && textureView.isAvailable && device == null) openCamera() }
    override fun onPause() { stopChaos(); closeCamera(); super.onPause() }
    private fun closeCamera() {
        try { session?.close(); session = null; device?.close(); device = null; imageReader?.close(); imageReader = null; lastPreviewSurface?.release(); lastPreviewSurface = null } catch (_: Exception) {}
    }
    override fun onDestroy() { stopChaos(); closeCamera(); stopBackgroundThread(); super.onDestroy() }
}
