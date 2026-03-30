package com.unlockSelfie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SelfieService : Service(), LifecycleOwner {

    companion object {
        const val EXTRA_TRIGGER = "trigger_type"
        const val TRIGGER_UNLOCK = "unlock"
        const val TRIGGER_WRONG_PASSWORD = "wrong_password"
        const val TRIGGER_BOOT = "boot"
        private const val CHANNEL_ID = "selfie_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "SelfieService"
        // Deep sleep timeout: stop service after 30s of idle
        private const val DEEP_SLEEP_TIMEOUT_MS = 30_000L
    }

    private lateinit var lifecycleRegistry: LifecycleRegistry
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isCapturing = false

    // Deep sleep runnable: release resources and stop service
    private val deepSleepRunnable = Runnable {
        Log.d(TAG, "Entering deep sleep mode - releasing resources")
        releaseCamera()
        stopSelf()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trigger = intent?.getStringExtra(EXTRA_TRIGGER) ?: TRIGGER_UNLOCK
        Log.d(TAG, "Service triggered by: $trigger")

        // Acquire wake lock briefly to complete capture
        acquireWakeLock()

        // Start as foreground
        startForeground(NOTIFICATION_ID, buildNotification())

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Cancel any pending deep sleep
        handler.removeCallbacks(deepSleepRunnable)

        if (!isCapturing) {
            startCapture()
        }

        // Schedule deep sleep after capture window
        handler.postDelayed(deepSleepRunnable, DEEP_SLEEP_TIMEOUT_MS)

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock?.release()
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UnlockSelfie:Capture")
            wakeLock?.acquire(15_000L) // 15 second max
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun startCapture() {
        isCapturing = true
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                this.imageCapture = imageCapture

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageCapture
                )

                // Small delay for camera warmup
                handler.postDelayed({ takePhoto() }, 500)
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                isCapturing = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: run {
            isCapturing = false
            return
        }

        val saveDir = File(Prefs.getSaveDir(this))
        if (!saveDir.exists()) saveDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File(saveDir, "selfie_$timestamp.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor ?: ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Selfie saved: ${photoFile.absolutePath}")
                    isCapturing = false
                    releaseCamera()
                    // Cancel pending deep sleep and stop immediately
                    handler.removeCallbacks(deepSleepRunnable)
                    stopSelf()
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    isCapturing = false
                    releaseCamera()
                    handler.removeCallbacks(deepSleepRunnable)
                    stopSelf()
                }
            }
        )
    }

    private fun releaseCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageCapture = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera", e)
        }
        try {
            cameraExecutor?.shutdown()
            cameraExecutor = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down executor", e)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_camera)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(deepSleepRunnable)
        releaseCamera()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        try {
            wakeLock?.release()
        } catch (e: Exception) { /* already released */ }
        super.onDestroy()
    }
}
