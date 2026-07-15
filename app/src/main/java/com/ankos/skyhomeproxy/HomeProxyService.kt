package com.ankos.skyhomeproxy

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class HomeProxyService : AccessibilityService() {

    companion object {
        private const val CHANNEL_ID = "sky_home_proxy"
        private const val NOTIFICATION_ID = 1
        private const val COOLDOWN_MS = 700L
        private const val TARGET_PACKAGE = "com.skyui.launcher"
        private const val TARGET_ACTIVITY =
            "com.android.launcher3.uioverrides.QuickstepLauncher"
        private const val RECENTS_ACTIVITY =
            "com.android.quickstep.RecentsActivity"
        private const val NIAGARA_PACKAGE = "bitpit.launcher"
        private const val NIAGARA_ACTIVITY = "bitpit.launcher.ui.HomeActivity"

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colon = enabledServices.split(':')
            return colon.any { it.endsWith("HomeProxyService") }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastLaunchTime = 0L
    private var pendingLaunch: Runnable? = null
    private var seenRecents = false
    private var lastForegroundPkg = ""
    private var maskView: View? = null
    private var windowManager: WindowManager? = null

    private fun log(msg: String) {
        android.util.Log.d("HomeProxy", msg)
        LogBuffer.add("SVC", msg)
    }

    override fun onCreate() {
        super.onCreate()
        log("onCreate")
        createNotificationChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
        }
        serviceInfo = info

        createMaskOverlay()

        val delay = AppPrefs.getDelayMs(this)
        log("connected delay=${delay}ms")

        try {
            startForegroundNotification()
            log("foreground OK")
        } catch (e: Exception) {
            log("foreground fail: ${e.message}")
            postFallbackNotification()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return

        if (packageName != TARGET_PACKAGE) {
            if (packageName != "android" && packageName != "com.android.systemui") {
                lastForegroundPkg = packageName
            }
            return
        }

        if (AppPrefs.isPaused(this)) {
            if (className == TARGET_ACTIVITY || className == RECENTS_ACTIVITY) {
                log("paused: $className")
            }
            return
        }

        if (className == RECENTS_ACTIVITY) {
            seenRecents = true
            pendingLaunch?.let { handler.removeCallbacks(it) }
            pendingLaunch = null
            hideMask()
            log("recents, cancel")
            return
        }

        if (className != TARGET_ACTIVITY) return

        if (lastForegroundPkg == NIAGARA_PACKAGE) {
            log("already on Niagara, skip")
            return
        }

        log("Home detected (last=$lastForegroundPkg)")

        val now = System.currentTimeMillis()
        if (now - lastLaunchTime < COOLDOWN_MS) {
            log("cooldown ${now - lastLaunchTime}ms")
            return
        }

        seenRecents = false
        showMask()

        pendingLaunch?.let { handler.removeCallbacks(it) }
        pendingLaunch = Runnable {
            if (!seenRecents) {
                lastLaunchTime = System.currentTimeMillis()
                launchNiagara()
            }
            pendingLaunch = null
        }
        handler.postDelayed(pendingLaunch!!, AppPrefs.getDelayMs(this))
    }

    override fun onInterrupt() {
        log("onInterrupt")
    }

    override fun onDestroy() {
        log("onDestroy")
        handler.removeCallbacksAndMessages(null)
        destroyMaskOverlay()
        super.onDestroy()
    }

    private fun launchNiagara() {
        try {
            val intent = Intent().apply {
                setClassName(NIAGARA_PACKAGE, NIAGARA_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
            lastForegroundPkg = NIAGARA_PACKAGE
            log("-> Niagara")
                handler.postDelayed({ hideMask() }, 100L)
        } catch (e: Exception) {
            log("launch fail: ${e.message}")
        }
    }

    private fun createMaskOverlay() {
        if (maskView != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        maskView = View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
        }
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        try {
            windowManager!!.addView(maskView, params)
            maskView!!.visibility = View.GONE
        } catch (e: Exception) {
            log("mask create fail: " + e.message)
            maskView = null
        }
    }

    private fun showMask() {
        val view = maskView ?: return
        view.visibility = View.VISIBLE
        view.animate().cancel()
        view.animate().alpha(1f).setDuration(80).start()
    }

    private fun hideMask() {
        val view = maskView ?: return
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(80).withEndAction {
            view.visibility = View.GONE
        }.start()
    }

    private fun destroyMaskOverlay() {
        maskView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        maskView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
            }
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_running))
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_running))
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    private fun startForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun postFallbackNotification() {
        val notification = buildNotification()
        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
