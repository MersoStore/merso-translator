package com.merso.gametranslate

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream

class CameraActivity : Activity() {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var reader: ImageReader? = null
    private var lastFrameAt: Long = 0
    private var sensorOrientation: Int = 90
    private var extraRotation: Int = 0
    private var zoomLevel: Float = 1f
    private var maxZoom: Float = 4f
    private var activeArray: Rect? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private lateinit var caption: TextView
    private lateinit var debugLine: TextView
    private lateinit var cropBox: CropBoxView
    private lateinit var texture: TextureView
    private lateinit var rotateBtn: android.widget.Button

    private fun displayDegrees(): Int = when (windowManager.defaultDisplay.rotation) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        else -> 270
    }

    private fun totalRotation(): Int = (sensorOrientation - displayDegrees() + extraRotation + 360) % 360

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (viewWidth == 0 || viewHeight == 0) return
        val total = totalRotation()
        val matrix = Matrix()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        if (total == 90 || total == 270) {
            val bufferRect = android.graphics.RectF(0f, 0f, 720f, 1280f)
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(
                android.graphics.RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat()),
                bufferRect, Matrix.ScaleToFit.FILL
            )
            val scale = maxOf(viewHeight.toFloat() / 720f, viewWidth.toFloat() / 1280f)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(total.toFloat(), centerX, centerY)
        } else if (total == 180) {
            matrix.postRotate(180f, centerX, centerY)
        }
        texture.setTransform(matrix)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        texture = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(texture)

        cropBox = CropBoxView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            val c = Prefs.camCrop(this@CameraActivity)
            setFractions(c[0], c[1], c[2], c[3])
            onChanged = { l, t, r, b -> Prefs.setCamCrop(this@CameraActivity, l, t, r, b) }
        }
        root.addView(cropBox)

        val pinch = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                zoomLevel = (zoomLevel * detector.scaleFactor).coerceIn(1f, maxZoom)
                applyZoom()
                return true
            }
        })
        val tapDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                rotateBtn.visibility = if (rotateBtn.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                return true
            }
        })
        texture.setOnTouchListener { _, e ->
            pinch.onTouchEvent(e)
            tapDetector.onTouchEvent(e)
            true
        }

        caption = TextView(this).apply {
            text = Str.p("وجّه الكاميرا على الكلام الإنجليزي في الشاشة", "Point the camera at the English text on screen")
            textSize = 20f
            setTextColor(Color.parseColor("#FFC400"))
            setBackgroundColor(Color.parseColor("#CC000000"))
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            )
        }
        root.addView(caption)

        debugLine = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor("#FFC400"))
            setBackgroundColor(Color.parseColor("#88000000"))
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        }
        root.addView(debugLine)
        val dbgHandler = Handler(android.os.Looper.getMainLooper())
        dbgHandler.post(object : Runnable {
            override fun run() {
                val raw = OcrEngine.lastRawText
                val err = OcrEngine.lastError
                debugLine.text = when {
                    err.isNotEmpty() -> Str.p("غلط قراءة: ", "OCR error: ") + err
                    raw.isEmpty() -> Str.p("لسه مفيش نص اتقرأ", "No text read yet")
                    else -> Str.p("آخر قراءة: ", "Last read: ") + raw.take(60)
                }
                dbgHandler.postDelayed(this, 1200)
            }
        })

        rotateBtn = android.widget.Button(this).apply {
            text = Str.p("تدوير الصورة", "Rotate")
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            setOnClickListener {
                extraRotation = (extraRotation + 90) % 360
                if (texture.isAvailable) configureTransform(texture.width, texture.height)
            }
        }
        root.addView(rotateBtn)

        setContentView(root)

        MqttSender.connect(this)

        thread = HandlerThread("camera").apply { start() }
        handler = Handler(thread!!.looper)

        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                configureTransform(w, h)
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) =
                configureTransform(w, h)
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
        if (texture.isAvailable) openCamera()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: cm.cameraIdList.firstOrNull() ?: return
        val chars = cm.getCameraCharacteristics(id)
        sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 4f
        if (texture.isAvailable) configureTransform(texture.width, texture.height)

        reader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 2)
        reader?.setOnImageAvailableListener({ r ->
            val now = System.currentTimeMillis()
            val img: Image? = try { r.acquireLatestImage() } catch (_: Exception) { null }
            if (img == null) return@setOnImageAvailableListener
            if (now - lastFrameAt < 900) { img.close(); return@setOnImageAvailableListener }
            lastFrameAt = now
            try {
                val bmp = yuvToBitmap(img)
                if (bmp != null) {
                    // Only the text inside the on-screen box gets translated
                    val cropped = OcrEngine.cropBitmap(bmp, Prefs.camCrop(this@CameraActivity))
                    if (cropped !== bmp) bmp.recycle()
                    OcrEngine.processFrame(cropped, immediate = true) { en, ar, color ->
                        MqttSender.sendCaption(this@CameraActivity, en, ar, -1, 0.5f, 0.9f, if (Prefs.autoColor(this@CameraActivity)) color else null)
                        History.add(this@CameraActivity, en, ar)
                        Tts.speak(this@CameraActivity, ar)
                        runOnUiThread { caption.text = ar }
                    }
                }
            } catch (_: Exception) {
            } finally {
                img.close()
            }
        }, handler)

        cm.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(cam: CameraDevice) {
                device = cam
                startSession(cam)
            }
            override fun onDisconnected(cam: CameraDevice) { cam.close() }
            override fun onError(cam: CameraDevice, error: Int) { cam.close() }
        }, handler)
    }

    private fun startSession(cam: CameraDevice) {
        val st = texture.surfaceTexture ?: return
        st.setDefaultBufferSize(1280, 720)
        val previewSurface = Surface(st)
        val readerSurface = reader?.surface ?: return
        val surfaces = listOf(previewSurface, readerSurface)
        cam.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                previewBuilder = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface)
                    addTarget(readerSurface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
                applyZoom()
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, handler)
    }

    private fun applyZoom() {
        val session = captureSession ?: return
        val builder = previewBuilder ?: return
        val arr = activeArray
        if (arr != null && zoomLevel > 1f) {
            val w = (arr.width() / zoomLevel).toInt()
            val h = (arr.height() / zoomLevel).toInt()
            val l = arr.centerX() - w / 2
            val t = arr.centerY() - h / 2
            builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(l, t, l + w, t + h))
        } else if (arr != null) {
            builder.set(CaptureRequest.SCALER_CROP_REGION, arr)
        }
        try { session.setRepeatingRequest(builder.build(), null, handler) } catch (_: Exception) {}
    }

    private fun yuvToBitmap(image: Image): Bitmap? {
        val w = image.width
        val h = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        // Stride-aware YUV_420_888 -> NV21 (planes may have row padding /
        // interleaved chroma; a naive copy scrambles the image on many phones)
        val nv21 = ByteArray(w * h * 3 / 2)
        var pos = 0
        val yBuf = yPlane.buffer
        if (yPlane.pixelStride == 1) {
            for (row in 0 until h) {
                yBuf.position(row * yPlane.rowStride)
                yBuf.get(nv21, pos, w)
                pos += w
            }
        } else {
            for (row in 0 until h) for (col in 0 until w) {
                nv21[pos++] = yBuf.get(row * yPlane.rowStride + col * yPlane.pixelStride)
            }
        }
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        for (row in 0 until h / 2) for (col in 0 until w / 2) {
            nv21[pos++] = vBuf.get(row * vPlane.rowStride + col * vPlane.pixelStride)
            nv21[pos++] = uBuf.get(row * uPlane.rowStride + col * uPlane.pixelStride)
        }
        val yuv = YuvImage(nv21, ImageFormat.NV21, w, h, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, w, h), 80, out)
        var bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size()) ?: return null
        val degrees = totalRotation()
        if (degrees != 0) {
            val m = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }
        return bmp
    }

    override fun onDestroy() {
        super.onDestroy()
        try { device?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        thread?.quitSafely()
    }

    override fun onBackPressed() {
        Nav.goMode(this)
    }
}

// Draggable/resizable rectangle over the camera preview. Only text inside it
// is translated. The area outside is lightly dimmed so the box is clear
// without blocking the view.
class CropBoxView(ctx: Context) : View(ctx) {
    private var l = 0.15f
    private var t = 0.55f
    private var r = 0.85f
    private var b = 0.95f
    var onChanged: ((Float, Float, Float, Float) -> Unit)? = null

    private val dimPaint = android.graphics.Paint().apply { color = Color.parseColor("#59000000") }
    private val borderPaint = android.graphics.Paint().apply {
        color = Color.parseColor("#FFC400")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val handlePaint = android.graphics.Paint().apply { color = Color.parseColor("#FFC400") }

    private var dragMode = 0 // 0 none, 1 move, 2 resize
    private var lastX = 0f
    private var lastY = 0f

    fun setFractions(nl: Float, nt: Float, nr: Float, nb: Float) {
        l = nl; t = nt; r = nr; b = nb
        invalidate()
    }

    private fun rect() = android.graphics.RectF(l * width, t * height, r * width, b * height)

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val rc = rect()
        // dim everything outside the box
        canvas.drawRect(0f, 0f, width.toFloat(), rc.top, dimPaint)
        canvas.drawRect(0f, rc.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, rc.top, rc.left, rc.bottom, dimPaint)
        canvas.drawRect(rc.right, rc.top, width.toFloat(), rc.bottom, dimPaint)
        canvas.drawRect(rc, borderPaint)
        val hs = 14f
        canvas.drawRect(rc.right - hs, rc.bottom - hs, rc.right + hs, rc.bottom + hs, handlePaint)
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        val rc = rect()
        val edge = 48f
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y
                dragMode = when {
                    kotlin.math.abs(e.x - rc.right) < edge && kotlin.math.abs(e.y - rc.bottom) < edge -> 2
                    e.x in rc.left..rc.right && e.y in rc.top..rc.bottom -> 1
                    else -> 0
                }
                if (dragMode != 0) parent?.requestDisallowInterceptTouchEvent(true)
                return dragMode != 0
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (dragMode == 0) return false
                val dx = (e.x - lastX) / width
                val dy = (e.y - lastY) / height
                lastX = e.x; lastY = e.y
                if (dragMode == 1) {
                    val w = r - l; val h = b - t
                    l = (l + dx).coerceIn(0f, 1f - w)
                    t = (t + dy).coerceIn(0f, 1f - h)
                    r = l + w; b = t + h
                } else {
                    r = (r + dx).coerceIn(l + 0.15f, 1f)
                    b = (b + dy).coerceIn(t + 0.1f, 1f)
                }
                invalidate()
                onChanged?.invoke(l, t, r, b)
                return true
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                val was = dragMode
                dragMode = 0
                return was != 0
            }
        }
        return false
    }
}
