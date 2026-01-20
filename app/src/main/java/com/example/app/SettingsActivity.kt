package com.example.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        topAppBar.title = getString(R.string.settings)
        topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val bgIndexingSwitch = findViewById<SwitchCompat>(R.id.bgIndexingSwitch)
        val lowDisturbanceSwitch = findViewById<SwitchCompat>(R.id.lowDisturbanceSwitch)

        bgIndexingSwitch.isChecked = AppSettings.isBackgroundIndexingEnabled(this)
        lowDisturbanceSwitch.isChecked = AppSettings.isLowDisturbanceModeEnabled(this)

        bgIndexingSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setBackgroundIndexingEnabled(this, isChecked)
            // Apply immediately.
            BackgroundIndexScheduler.ensureScheduled(this)
        }

        lowDisturbanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setLowDisturbanceModeEnabled(this, isChecked)
        }
    }

    companion object {
        fun intent(context: android.content.Context): android.content.Intent {
            return android.content.Intent(context, SettingsActivity::class.java)
        }
    }
}
