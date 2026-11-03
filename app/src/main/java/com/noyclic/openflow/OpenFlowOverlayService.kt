package com.noyclic.openflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OpenFlowOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var paused = false
    private val state by lazy { getSharedPreferences("openflow_state", Context.MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> paused = true
            ACTION_RESUME -> paused = false
            ACTION_START -> paused = false
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        attachOrRefreshOverlay()
        state.edit().putBoolean("overlay_paused", paused).apply()
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            })
        }
        val openApp = PendingIntent.getActivity(
            this, 100, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 101, Intent(this, OpenFlowOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (paused) "OpenFlow is paused" else "OpenFlow is active")
            .setContentText("Tap to open controls. Stop any time from this notification.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun attachOrRefreshOverlay() {
        if (windowManager == null) windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val manager = windowManager ?: return
        overlayView?.let { runCatching { manager.removeView(it) }; overlayView = null }

        val windowType = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = layoutParams ?: WindowManager.LayoutParams(
            dp(272), WindowManager.LayoutParams.WRAP_CONTENT, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(14)
            y = dp(116)
        }.also { layoutParams = it }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(14))
            background = panelBackground()
            elevation = dp(10).toFloat()
        }
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "OPENFLOW  •  ${if (paused) "PAUSED" else "LIVE"}"
            textSize = 11f
            setTextColor(Color.parseColor(if (paused) "#F0C27A" else "#69E4C5"))
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            letterSpacing = 0.07f
        }
        titleBar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val dot = TextView(this).apply {
            text = if (paused) "Ⅱ" else "●"
            textSize = 13f
            setTextColor(Color.parseColor(if (paused) "#F0C27A" else "#69E4C5"))
        }
        titleBar.addView(dot)
        panel.addView(titleBar)

        val subtitle = TextView(this).apply {
            text = if (paused) "Automation is paused. You're in control." else "Floating companion is ready."
            textSize = 12f
            setTextColor(Color.parseColor("#A9BBD0"))
            setPadding(0, dp(7), 0, dp(12))
        }
        panel.addView(subtitle)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pauseButton = overlayButton(if (paused) "RESUME" else "PAUSE", "#1D3C43", "#B8F5E6")
        pauseButton.setOnClickListener {
            val next = if (paused) ACTION_RESUME else ACTION_PAUSE
            onStartCommand(Intent(this, OpenFlowOverlayService::class.java).setAction(next), 0, 0)
        }
        val stopButton = overlayButton("STOP", "#422D38", "#FFC0C8")
        stopButton.setOnClickListener { stopSelf() }
        actions.addView(pauseButton, LinearLayout.LayoutParams(0, dp(38), 1f))
        actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        actions.addView(stopButton, LinearLayout.LayoutParams(0, dp(38), 1f))
        panel.addView(actions)

        titleBar.setOnTouchListener { _, event -> handleDrag(event, params, manager, panel) }
        overlayView = panel
        try {
            manager.addView(panel, params)
            state.edit().putBoolean("overlay_running", true).putBoolean("overlay_paused", paused).apply()
        } catch (_: Exception) {
            overlayView = null
            stopSelf()
        }
    }

    private var dragStartX = 0
    private var dragStartY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f

    private fun handleDrag(
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        manager: WindowManager,
        panel: View
    ): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = params.x
                dragStartY = params.y
                dragTouchX = event.rawX
                dragTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = (dragStartX - (event.rawX - dragTouchX)).toInt().coerceAtLeast(0)
                params.y = (dragStartY + (event.rawY - dragTouchY)).toInt().coerceAtLeast(0)
                runCatching { manager.updateViewLayout(panel, params) }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    private fun overlayButton(label: String, fill: String, foreground: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(foreground))
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(fill))
                cornerRadius = dp(10).toFloat()
            }
        }
    }

    private fun panelBackground() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor("#17283A"), Color.parseColor("#101A28"))
    ).apply {
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), Color.parseColor("#3C566F"))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlayView = null
        state.edit().putBoolean("overlay_running", false).putBoolean("overlay_paused", false).apply()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.noyclic.openflow.action.START"
        const val ACTION_PAUSE = "com.noyclic.openflow.action.PAUSE"
        const val ACTION_RESUME = "com.noyclic.openflow.action.RESUME"
        const val ACTION_STOP = "com.noyclic.openflow.action.STOP"
        private const val CHANNEL_ID = "openflow_companion"
        private const val NOTIFICATION_ID = 7401
    }
}
