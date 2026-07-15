package com.ankos.skyhomeproxy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
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

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show()
            }
        }

    private val logListener: () -> Unit = {
        handler.post { refreshLogs() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvLog.movementMethod = ScrollingMovementMethod()

        binding.btnService.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnPause.setOnClickListener {
            isPaused = !isPaused
            AppPrefs.setPaused(this, isPaused)
            updatePauseButton()
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
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshPrefs()
        refreshLogs()
        LogBuffer.addListener(logListener)
        requestNotificationPermission()
    }

    override fun onPause() {
        super.onPause()
        LogBuffer.removeListener(logListener)
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
        binding.tvLog.text = LogBuffer.logs.joinToString("\n")
        val layout = binding.tvLog.layout ?: return
        val lineCount = layout.lineCount
        if (lineCount > 0) {
            val scrollY = layout.getLineTop(lineCount) - binding.tvLog.height
            if (scrollY > 0) binding.tvLog.scrollTo(0, scrollY)
        }
    }
}
