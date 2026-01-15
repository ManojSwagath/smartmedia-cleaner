package com.example.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<TextView>(R.id.titleText).text = getString(R.string.settings)

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
