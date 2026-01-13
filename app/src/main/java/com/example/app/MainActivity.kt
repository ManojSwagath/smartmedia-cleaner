package com.example.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.pow

class MainActivity : AppCompatActivity() {

    private lateinit var analyzeButton: Button
    private lateinit var loadingBar: ProgressBar
    private lateinit var totalImagesText: TextView
    private lateinit var totalSpaceText: TextView

    private val permissionToRequest: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                runImageScan()
            } else {
                showPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        analyzeButton = findViewById(R.id.analyzeButton)
        loadingBar = findViewById(R.id.loadingBar)
        totalImagesText = findViewById(R.id.totalImagesText)
        totalSpaceText = findViewById(R.id.totalSpaceText)

        analyzeButton.setOnClickListener {
            if (hasStoragePermission()) {
                runImageScan()
            } else {
                requestStoragePermission()
            }
        }
    }

    private fun hasStoragePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, permissionToRequest) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestStoragePermission() {
        permissionLauncher.launch(permissionToRequest)
    }

    private fun showPermissionDenied() {
        Toast.makeText(
            this,
            getString(R.string.permission_denied),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun runImageScan() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { scanImages() }
                totalImagesText.fadeToText(getString(R.string.total_images_value, result.count))
                totalSpaceText.fadeToText(getString(R.string.total_size_value, "${result.sizeGb} GB"))
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.scan_failed, e.message ?: "error"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun scanImages(): ScanResult {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE
        )

        var count = 0
        var totalBytes = 0L

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                // Read the columns so the query is actually exercised.
                cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn)
                totalBytes += size
                count++
            }
        }

        return ScanResult(
            count = count,
            totalBytes = totalBytes,
            sizeGb = bytesToGbString(totalBytes)
        )
    }

    private fun bytesToGbString(bytes: Long): String {
        val gb = bytes.toDouble() / 1024.0.pow(3.0)
        return String.format(Locale.US, "%.2f", gb)
    }

    private fun showLoading(show: Boolean) {
        loadingBar.visibility = if (show) View.VISIBLE else View.GONE
        analyzeButton.isEnabled = !show
        analyzeButton.alpha = if (show) 0.6f else 1f
    }

    private fun TextView.fadeToText(newText: String) {
        animate().alpha(0f).setDuration(120).withEndAction {
            text = newText
            animate().alpha(1f).setDuration(180).start()
        }.start()
    }

    private data class ScanResult(
        val count: Int,
        val totalBytes: Long,
        val sizeGb: String
    )

    companion object {
        // No constants needed.
    }
}
