package com.smriti.app.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {
        const val TAG = "SmritiScreenCap"
        const val ACTION_START = "com.smriti.app.capture.action.START"
        const val ACTION_CAPTURE = "com.smriti.app.capture.action.CAPTURE"
        const val ACTION_STOP = "com.smriti.app.capture.action.STOP"
        const val ACTION_CAPTURED = "com.smriti.app.capture.action.CAPTURED"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_FILE_PATH = "extra_file_path"

        const val CHANNEL_ID = "smriti_screencap"
        private const val NOTIFICATION_ID = 2001

        const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

        @Volatile
        private var armed: Boolean = false

        fun isArmed(): Boolean = armed

        @Volatile
        var onCaptureCallback: ((File) -> Unit)? = null

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun capture(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_CAPTURE
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val captureMutex = Mutex()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var idleJob: Job? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopProjection("projection callback onStop")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                handleActionStart(resultCode, resultData)
            }
            ACTION_CAPTURE -> {
                handleActionCapture()
            }
            ACTION_STOP -> {
                stopProjection("action stop")
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen capture armed",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for active screen capture service"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        createNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen capture armed")
            .setContentText("Ready to capture screen")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun handleActionStart(resultCode: Int, resultData: Intent?) {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Cannot start MediaProjection: invalid resultCode ($resultCode) or null resultData")
            stopProjection("invalid consent data")
            stopSelf()
            return
        }

        if (mediaProjection != null || virtualDisplay != null) {
            stopProjection("re-arming with new consent")
        }

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (projectionManager == null) {
                Log.e(TAG, "MediaProjectionManager not available")
                stopProjection("media projection manager unavailable")
                stopSelf()
                return
            }

            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                Log.e(TAG, "getMediaProjection returned null")
                stopProjection("media projection null")
                stopSelf()
                return
            }
            mediaProjection = projection

            // Android 14+ requires callback registration before creating virtual display
            projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val (width, height, densityDpi) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                Triple(bounds.width(), bounds.height(), resources.configuration.densityDpi)
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
            }

            val safeWidth = if (width > 0) width else 1080
            val safeHeight = if (height > 0) height else 1920
            val safeDpi = if (densityDpi > 0) densityDpi else DisplayMetrics.DENSITY_DEFAULT

            val reader = ImageReader.newInstance(safeWidth, safeHeight, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            val vDisplay = projection.createVirtualDisplay(
                "smriti_screencap",
                safeWidth,
                safeHeight,
                safeDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            )
            virtualDisplay = vDisplay

            armed = true
            Log.i(TAG, "armed")
            resetIdleTimeout()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to arm screen capture: ${t.message}", t)
            stopProjection("arm failed: ${t.message}")
            stopSelf()
        }
    }

    private fun handleActionCapture() {
        if (!armed || imageReader == null) {
            Log.w(TAG, "Capture requested but service is not armed")
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            captureScreen()
        }
    }

    private suspend fun captureScreen() {
        captureMutex.withLock {
            val reader = imageReader
            if (reader == null || !armed) {
                Log.w(TAG, "Cannot capture: service not armed")
                return
            }

            var image = reader.acquireLatestImage()
            if (image == null) {
                for (i in 0 until 20) {
                    delay(50)
                    image = reader.acquireLatestImage()
                    if (image != null) break
                }
            }

            if (image == null) {
                Log.w(TAG, "Failed to capture: no image available from ImageReader")
                return
            }

            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val width = image.width
                val height = image.height

                val rowBytes = width * pixelStride
                val rowPadding = rowStride - rowBytes

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                if (rowPadding == 0 && buffer.remaining() >= rowBytes * height) {
                    buffer.position(0)
                    bitmap.copyPixelsFromBuffer(buffer)
                } else {
                    val cleanBuffer = ByteBuffer.allocateDirect(rowBytes * height)
                    for (row in 0 until height) {
                        val rowStart = row * rowStride
                        buffer.position(rowStart)
                        val oldLimit = buffer.limit()
                        val bytesToRead = minOf(rowBytes, maxOf(0, buffer.capacity() - rowStart))
                        buffer.limit(rowStart + bytesToRead)
                        cleanBuffer.put(buffer)
                        buffer.limit(oldLimit)
                        if (bytesToRead < rowBytes) {
                            cleanBuffer.position(cleanBuffer.position() + (rowBytes - bytesToRead))
                        }
                    }
                    cleanBuffer.rewind()
                    bitmap.copyPixelsFromBuffer(cleanBuffer)
                }

                val photosDir = File(filesDir, "photos").apply {
                    if (!exists()) {
                        mkdirs()
                    }
                }
                val photoFile = File(photosDir, "${System.currentTimeMillis()}.jpg")
                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                }
                bitmap.recycle()

                Log.i(TAG, "captured: ${photoFile.absolutePath} (${photoFile.length()} bytes)")

                val broadcastIntent = Intent(ACTION_CAPTURED).apply {
                    putExtra(EXTRA_FILE_PATH, photoFile.absolutePath)
                    setPackage(packageName)
                }
                sendBroadcast(broadcastIntent)
                onCaptureCallback?.invoke(photoFile)
                resetIdleTimeout()
            } catch (t: Throwable) {
                Log.e(TAG, "Error during capture: ${t.message}", t)
            } finally {
                image.close()
            }
        }
    }

    private fun resetIdleTimeout() {
        idleJob?.cancel()
        idleJob = serviceScope.launch {
            delay(IDLE_TIMEOUT_MS)
            stopProjection("idle timeout (5 minutes with no capture)")
            stopSelf()
        }
    }

    private fun stopProjection(reason: String) {
        val wasArmed = armed
        armed = false
        idleJob?.cancel()
        idleJob = null

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing virtual display: ${e.message}")
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing image reader: ${e.message}")
        }
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media projection: ${e.message}")
        }
        mediaProjection = null

        if (wasArmed) {
            Log.i(TAG, "released: $reason")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProjection("service destroyed")
        serviceScope.cancel()
    }
}
