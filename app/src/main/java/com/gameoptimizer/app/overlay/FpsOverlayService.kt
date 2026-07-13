package com.gameoptimizer.app.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.gameoptimizer.app.shizuku.ShizukuManager

/**
 * Muestra un overlay flotante con el FPS real del juego objetivo,
 * calculado a partir de "dumpsys gfxinfo <paquete> framestats" vía Shizuku.
 */
class FpsOverlayService : Service() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
        private const val CHANNEL_ID = "fps_overlay_channel"
        private const val NOTIF_ID = 42
        private const val UPDATE_INTERVAL_MS = 1000L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: TextView
    private var targetPackage: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameCount = 0L

    private val updateRunnable = object : Runnable {
        override fun run() {
            val fps = readFps(targetPackage)
            overlayView.text = "FPS: $fps"
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
        startForeground(NOTIF_ID, buildNotification())
        // Limpiar buffer de stats para empezar a medir desde cero
        ShizukuManager.runShellCommand("dumpsys gfxinfo $targetPackage reset")
        handler.post(updateRunnable)
        return START_STICKY
    }

    private fun createOverlayView() {
        overlayView = TextView(this).apply {
            text = "FPS: --"
            setTextColor(Color.GREEN)
            textSize = 14f
            setBackgroundColor(Color.parseColor("#88000000"))
            setPadding(16, 8, 16, 8)
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 100

        windowManager.addView(overlayView, params)
    }

    /**
     * Lee "dumpsys gfxinfo <pkg> framestats" y calcula FPS real a partir
     * de los timestamps de frames renderizados en el último segundo.
     */
    private fun readFps(pkg: String): Int {
        if (pkg.isEmpty()) return 0
        val output = ShizukuManager.runShellCommand("dumpsys gfxinfo $pkg framestats")
        return parseFpsFromFrameStats(output)
    }

    private fun parseFpsFromFrameStats(raw: String) : Int {
        // El bloque de framestats trae líneas CSV donde la primera columna
        // es el VSYNC timestamp (en nanosegundos) de cada frame dibujado.
        val timestamps = mutableListOf<Long>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed[0].isDigit()) continue
            val firstField = trimmed.substringBefore(',')
            firstField.toLongOrNull()?.let { timestamps.add(it) }
        }
        if (timestamps.size < 2) return 0

        // Contar cuántos timestamps caen dentro del último segundo (en ns)
        val newest = timestamps.maxOrNull() ?: return 0
        val oneSecondAgo = newest - 1_000_000_000L
        val framesInLastSecond = timestamps.count { it in oneSecondAgo..newest }
        return framesInLastSecond
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Overlay de FPS", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Game Optimizer")
            .setContentText("Midiendo FPS de $targetPackage")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
