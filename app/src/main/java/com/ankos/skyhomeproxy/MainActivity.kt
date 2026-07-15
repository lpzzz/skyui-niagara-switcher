package com.ankos.skyhomeproxy

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.ankos.skyhomeproxy.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEnable.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun updateStatus() {
        val enabled = HomeProxyService.isEnabled(this)
        binding.tvStatus.text = getString(
            if (enabled) R.string.status_enabled else R.string.status_disabled
        )
        binding.btnEnable.text = getString(
            if (enabled) R.string.btn_settings else R.string.btn_enable
        )
    }
}
