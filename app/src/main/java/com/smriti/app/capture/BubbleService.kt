package com.smriti.app.capture

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smriti.app.MainActivity
import com.smriti.app.R
import com.smriti.app.data.SmritiDb
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.hypot

class BubbleService : Service() {

    companion object {
        const val TAG = "SmritiBubble"
        const val ACTION_START = "com.smriti.app.capture.action.START_BUBBLE"
        const val ACTION_STOP = "com.smriti.app.capture.action.STOP_BUBBLE"

        const val CHANNEL_ID = "smriti_bubble"
        private const val NOTIFICATION_ID = 2002

        private const val PREFS_NAME = "smriti_bubble_prefs"
        private const val PREF_BUBBLE_X = "bubble_x"
        private const val PREF_BUBBLE_Y = "bubble_y"
        private const val LONG_PRESS_TIMEOUT_MS = 400L

        @Volatile
        private var running: Boolean = false

        fun isRunning(): Boolean = running

        fun start(context: Context) {
            val intent = Intent(context, BubbleService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BubbleService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var normalDrawable: GradientDrawable
    private lateinit var liveDrawable: GradientDrawable

    private lateinit var pipeline: BubbleCapturePipeline
    private lateinit var asr: Asr

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var bubbleSizeNormal: Int = 0
    private var bubbleSizeLive: Int = 0
    private var isBubbleLive: Boolean = false

    private var downRawX = 0f
    private var downRawY = 0f
    private var initialX = 0
    private var initialY = 0
    private var isDragging = false
    private var isLongPressed = false
    private var touchSlop = 0f

    @Volatile
    private var userReleased = false
    @Volatile
    private var pendingPhoto: CompletableDeferred<File>? = null
    private var captureJob: Job? = null

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenCaptureService.ACTION_CAPTURED) {
                val path = intent.getStringExtra(ScreenCaptureService.EXTRA_FILE_PATH)
                if (path != null) {
                    val file = File(path)
                    pendingPhoto?.complete(file)
                }
            }
        }
    }

    private val longPressRunnable = Runnable {
        if (!isDragging) {
            isLongPressed = true
            startCapture()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Capture bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for persistent capture bubble overlay"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        createNotificationChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smriti capture bubble")
            .setContentText("Long-press the bubble to capture and narrate")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun startForegroundServiceNotification() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot start BubbleService: overlay permission not granted")
            stopForegroundNotification()
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        bubbleSizeNormal = (60 * density).toInt()
        bubbleSizeLive = (72 * density).toInt()

        // Amber colors matching theme (0xFFF2B705)
        normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xCCF2B705.toInt()) // Translucent circular amber
            setStroke((2 * density).toInt(), 0x66FFFFFF.toInt())
        }

        liveDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFF2B705.toInt()) // Vivid bright amber
            setStroke((4 * density).toInt(), 0xFFE53935.toInt()) // Red recording alert ring
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            bubbleSizeNormal,
            bubbleSizeNormal,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        loadSavedPosition()

        bubbleView = View(this).apply {
            background = normalDrawable
        }

        bubbleView.setOnTouchListener { _, event ->
            handleTouchEvent(event)
        }

        // Initialize capture pipeline with Whisper ASR
        val dao = SmritiDb.get(applicationContext).recordDao()
        asr = AsrFactory.create(applicationContext)
        pipeline = BubbleCapturePipeline(applicationContext, dao, asr)

        val filter = IntentFilter(ScreenCaptureService.ACTION_CAPTURED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(captureReceiver, filter)
        }

        attachBubbleView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.i(TAG, "Restarted with null intent (sticky restart); re-attaching bubble overlay")
            attachBubbleView()
            return START_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> {
                stopForegroundNotification()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                attachBubbleView()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
                attachBubbleView()
            }
        }
        return START_STICKY
    }

    // Tracked explicitly rather than via View.isAttachedToWindow: attachment is asynchronous,
    // so isAttachedToWindow is still false immediately after addView() returns. onCreate and
    // onStartCommand both used to call this, the second call passed the stale guard, and
    // WindowManager threw "has already been added" -- which then stopped the whole service.
    @Volatile
    private var bubbleAttached = false

    private fun attachBubbleView() {
        if (!::bubbleView.isInitialized || bubbleAttached) return
        try {
            windowManager.addView(bubbleView, layoutParams)
            bubbleAttached = true
            running = true
            Log.i(TAG, "Bubble overlay attached")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add bubble overlay: ${e.message}", e)
            stopForegroundNotification()
            stopSelf()
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                initialX = layoutParams.x
                initialY = layoutParams.y
                isDragging = false
                isLongPressed = false

                handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY

                if (!isLongPressed) {
                    if (!isDragging) {
                        if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            isDragging = true
                            handler.removeCallbacks(longPressRunnable)
                        }
                    }
                    if (isDragging) {
                        layoutParams.x = (initialX + dx).toInt()
                        layoutParams.y = (initialY + dy).toInt()
                        clampLayoutParams()
                        try {
                            windowManager.updateViewLayout(bubbleView, layoutParams)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to updateViewLayout on drag: ${e.message}")
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)

                if (isDragging) {
                    savePosition()
                } else if (isLongPressed) {
                    stopCaptureAndSave()
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    Toast.makeText(this, "Hold to capture", Toast.LENGTH_SHORT).show()
                }

                isDragging = false
                isLongPressed = false
                return true
            }
            else -> return false
        }
    }

    private fun startCapture() {
        userReleased = false
        setBubbleLive(true)

        if (!ScreenCaptureService.isArmed()) {
            Log.w(TAG, "Screen capture not armed when capture initiated")
            Toast.makeText(this, "Screen capture not armed", Toast.LENGTH_SHORT).show()
            val consentIntent = Intent(this, ScreenCaptureConsentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(consentIntent)
            setBubbleLive(false)
            isLongPressed = false
            return
        }

        // IMPORTANT ORDERING: request screenshot FIRST, before starting the recorder.
        val deferred = CompletableDeferred<File>()
        pendingPhoto = deferred
        ScreenCaptureService.onCaptureCallback = { file ->
            deferred.complete(file)
        }
        ScreenCaptureService.capture(this)

        captureJob?.cancel()
        captureJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val photoFile = withTimeoutOrNull(5000L) {
                    deferred.await()
                }
                if (photoFile == null || !photoFile.exists()) {
                    Log.e(TAG, "Screen capture timed out or file missing")
                    return@launch
                }

                if (userReleased) {
                    // If user already released before photo arrived, signal stopListening
                    // continuously to ensure asr.transcribe exits promptly without hanging.
                    serviceScope.launch(Dispatchers.IO) {
                        for (i in 0 until 10) {
                            (asr as? PushToTalk)?.stopListening()
                            delay(50)
                        }
                    }
                }

                val recordId = pipeline.capture(photoFile)
                Log.i(TAG, "Bubble capture saved record $recordId for ${photoFile.name}")
            } catch (t: Throwable) {
                Log.e(TAG, "Bubble capture error: ${t.message}", t)
            } finally {
                withContext(Dispatchers.Main) {
                    setBubbleLive(false)
                }
            }
        }
    }

    private fun stopCaptureAndSave() {
        userReleased = true
        (asr as? PushToTalk)?.stopListening()
    }

    private fun setBubbleLive(isLive: Boolean) {
        if (isLive == isBubbleLive) return
        isBubbleLive = isLive

        if (!::bubbleView.isInitialized) return
        bubbleView.background = if (isLive) liveDrawable else normalDrawable

        val oldSize = if (isLive) bubbleSizeNormal else bubbleSizeLive
        val newSize = if (isLive) bubbleSizeLive else bubbleSizeNormal
        val diff = (newSize - oldSize) / 2

        layoutParams.x -= diff
        layoutParams.y -= diff
        layoutParams.width = newSize
        layoutParams.height = newSize
        clampLayoutParams()

        if (bubbleView.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(bubbleView, layoutParams)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to updateViewLayout on live toggle: ${e.message}")
            }
        }
    }

    private fun loadSavedPosition() {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val defaultX = displayMetrics.widthPixels - bubbleSizeNormal - (16 * density).toInt()
        val defaultY = displayMetrics.heightPixels / 2 - bubbleSizeNormal / 2

        layoutParams.x = prefs.getInt(PREF_BUBBLE_X, defaultX)
        layoutParams.y = prefs.getInt(PREF_BUBBLE_Y, defaultY)
        clampLayoutParams()
    }

    private fun savePosition() {
        prefs.edit()
            .putInt(PREF_BUBBLE_X, layoutParams.x)
            .putInt(PREF_BUBBLE_Y, layoutParams.y)
            .apply()
    }

    private fun clampLayoutParams() {
        val displayMetrics = resources.displayMetrics
        val maxX = displayMetrics.widthPixels - layoutParams.width
        val maxY = displayMetrics.heightPixels - layoutParams.height
        layoutParams.x = layoutParams.x.coerceIn(0, maxOf(0, maxX))
        layoutParams.y = layoutParams.y.coerceIn(0, maxOf(0, maxY))
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        stopForegroundNotification()
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        captureJob?.cancel()
        ScreenCaptureService.onCaptureCallback = null

        try {
            unregisterReceiver(captureReceiver)
        } catch (_: Exception) {}

        if (::bubbleView.isInitialized && bubbleView.isAttachedToWindow) {
            try {
                windowManager.removeView(bubbleView)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove bubble view: ${e.message}")
            }
        }
        bubbleAttached = false
        Log.i(TAG, "BubbleService destroyed")
    }
}
