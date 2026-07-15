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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class HomeProxyService : AccessibilityService() {

    companion object {
        private const val TAG = "HomeProxyService"
        private const val CHANNEL_ID = "sky_home_proxy"
        private const val NOTIFICATION_ID = 1
        private const val COOLDOWN_MS = 700L
        private const val LAUNCH_DELAY_MS = 0L
        private const val TARGET_PACKAGE = "com.skyui.launcher"
        private const val TARGET_ACTIVITY =
            "com.android.launcher3.uioverrides.QuickstepLauncher"
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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        try {
            startForegroundNotification()
            Log.d(TAG, "foreground notification started")
        } catch (e: Exception) {
            Log.w(TAG, "foreground notification failed", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
        }
        serviceInfo = info
        Log.d(TAG, "service connected, notificationTimeout=50")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return

        if (packageName != TARGET_PACKAGE) return
        if (className != TARGET_ACTIVITY) return

        Log.d(TAG, "matched SkyUI Home")

        val now = System.currentTimeMillis()
        if (now - lastLaunchTime < COOLDOWN_MS) {
            Log.d(TAG, "cooldown, skip")
            return
        }

        // 首次命中先记时间，阻止冷却期内重复触发
        lastLaunchTime = now

        handler.postDelayed({
            launchNiagara()
        }, LAUNCH_DELAY_MS)
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun launchNiagara() {
        try {
            val intent = Intent().apply {
                setClassName(NIAGARA_PACKAGE, NIAGARA_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d(TAG, "niagara launched")
        } catch (e: Exception) {
            Log.w(TAG, "niagara launch failed", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
