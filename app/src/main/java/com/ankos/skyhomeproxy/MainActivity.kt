package com.ankos.skyhomeproxy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ankos.skyhomeproxy.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isPaused = false
    private var isLogTab = false

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show()
            }
        }

    private val logListener: () -> Unit = {
        handler.post { if (isLogTab) refreshLogs() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tabControl.setOnClickListener { switchTab(false) }
        binding.tabLog.setOnClickListener { switchTab(true) }

        binding.btnService.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnPause.setOnClickListener {
            isPaused = !isPaused
            AppPrefs.setPaused(this, isPaused)
            updatePauseButton()
            LogBuffer.add("UI", if (isPaused) "已暂停" else "已恢复")
        }

        binding.btnClearLog.setOnClickListener {
            LogBuffer.clear()
        }

        binding.seekDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvDelayValue.text = "${progress}ms"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                AppPrefs.setDelayMs(this@MainActivity, seekBar.progress.toLong())
                LogBuffer.add("UI", "延迟=${seekBar.progress}ms")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshPrefs()
        LogBuffer.addListener(logListener)
        requestNotificationPermission()
    }

    override fun onPause() {
        super.onPause()
        LogBuffer.removeListener(logListener)
    }

    private fun switchTab(log: Boolean) {
        isLogTab = log
        val activeColor = Color.parseColor("#1A73E8")
        val inactiveColor = Color.parseColor("#757575")

        binding.tabControl.backgroundTintList = ColorStateList.valueOf(
            if (!log) activeColor else Color.TRANSPARENT
        )
        binding.tabControl.setTextColor(if (!log) Color.WHITE else inactiveColor)
        binding.tabLog.backgroundTintList = ColorStateList.valueOf(
            if (log) activeColor else Color.TRANSPARENT
        )
        binding.tabLog.setTextColor(if (log) Color.WHITE else inactiveColor)

        binding.tabControl.visibility = if (log) android.view.View.VISIBLE else android.view.View.VISIBLE
        binding.tabLogContent.visibility = if (log) android.view.View.VISIBLE else android.view.View.GONE
        binding.tabControlContent.visibility = if (log) android.view.View.GONE else android.view.View.VISIBLE
        if (log) refreshLogs()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    private fun updateStatus() {
        val enabled = HomeProxyService.isEnabled(this)
        binding.tvStatus.text = getString(
            if (enabled) R.string.status_enabled else R.string.status_disabled
        )
        binding.btnService.text = getString(
            if (enabled) R.string.btn_settings else R.string.btn_enable
        )
    }

    private fun refreshPrefs() {
        isPaused = AppPrefs.isPaused(this)
        updatePauseButton()

        val delay = AppPrefs.getDelayMs(this).toInt()
        binding.seekDelay.progress = delay
        binding.tvDelayValue.text = "${delay}ms"
    }

    private fun updatePauseButton() {
        binding.btnPause.text = if (isPaused) "恢复代理" else "暂停代理"
    }

    private fun refreshLogs() {
        binding.tvLog.setText(LogBuffer.logs.joinToString("\n"))
        binding.tvLog.setSelection(binding.tvLog.text.length)
    }
}
