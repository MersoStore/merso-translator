package com.merso.gametranslate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager


class CaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.merso.gametranslate.STOP_CAPTURE"
        @Volatile var running: Boolean = false
        @Volatile var lastArabic: String = ""
        @Volatile var lastEnglish: String = ""
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastFrameAt: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1, buildNotification("الترجمة شغالة"))
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startCapture(resultCode, data)
        return START_STICKY
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("capture", "الترجمة", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "capture") else Notification.Builder(this)
        return b
            .setContentTitle(Str.p("مرسو للترجمة", "Merso Translator"))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        thread = HandlerThread("capture").apply { start() }
        handler = Handler(thread!!.looper)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture() }
        }, handler)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            val image: Image? = try { reader.acquireLatestImage() } catch (_: Exception) { null }
            if (image == null) return@setOnImageAvailableListener
            if (now - lastFrameAt < 800) { image.close(); return@setOnImageAvailableListener }
            lastFrameAt = now
            try {
                val bitmap = imageToBitmap(image, width, height)
                if (bitmap != null) {
                    val boxes = Prefs.crops(this@CaptureService)
                    OcrEngine.processBoxes(bitmap, boxes) { i, en, ar ->
                        lastEnglish = en
                        lastArabic = ar
                        val b = boxes[i]
                        MqttSender.sendCaption(this@CaptureService, en, ar, i, (b[0] + b[2]) / 2f, (b[1] + b[3]) / 2f)
                    }
                    bitmap.recycle()
                }
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = projection?.createVirtualDisplay(
            "MersoCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
        running = true
        MqttSender.connect(this)
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bmp = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, width, height)
    }

    private fun stopCapture() {
        running = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        projection = null
        thread?.quitSafely()
        thread = null
        handler = null
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}
