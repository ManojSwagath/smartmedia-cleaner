package com.example.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat

class MainActivity : AppCompatActivity() {

    private lateinit var analyzeButton: MaterialButton
    private lateinit var loadingBar: ProgressBar
    private lateinit var totalImagesText: TextView
    private lateinit var totalSpaceText: TextView
    private lateinit var lastScanText: TextView
    private lateinit var foldersRecycler: RecyclerView

    private lateinit var featureTiles: RecyclerView
    private lateinit var settingsButton: MaterialButton

    private lateinit var repository: MediaStoreRepository
    private lateinit var bucketAdapter: BucketAdapter
    private lateinit var featureTileAdapter: FeatureTileAdapter

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

        // Battery-friendly periodic indexing (runs when device allows).
        BackgroundIndexScheduler.ensureScheduled(this)

        analyzeButton = findViewById(R.id.analyzeButton)
        loadingBar = findViewById(R.id.loadingBar)
        totalImagesText = findViewById(R.id.totalImagesText)
        totalSpaceText = findViewById(R.id.totalSpaceText)
        lastScanText = findViewById(R.id.lastScanText)
        foldersRecycler = findViewById(R.id.foldersRecycler)

        featureTiles = findViewById(R.id.featureTiles)
        settingsButton = findViewById(R.id.settingsButton)

        repository = MediaStoreRepository(this)

        bucketAdapter = BucketAdapter { bucket ->
            startActivity(FolderDetailActivity.intent(this, bucket.bucketId, bucket.name))
        }
        foldersRecycler.layoutManager = LinearLayoutManager(this)
        foldersRecycler.adapter = bucketAdapter

        featureTileAdapter = FeatureTileAdapter { tile ->
            when (tile.id) {
                "whatsapp" -> {
                    val selection: String
                    val args: Array<String>
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        selection = "(${android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR ${android.provider.MediaStore.Images.Media.RELATIVE_PATH} LIKE ?)"
                        args = arrayOf("%WhatsApp%", "%WhatsApp%")
                    } else {
                        selection = "${android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
                        args = arrayOf("%WhatsApp%")
                    }
                    startActivity(
                        MediaQueryActivity.intent(
                            this,
                            title = getString(R.string.whatsapp_cleanup),
                            selection = selection,
                            selectionArgs = args,
                            sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
                        )
                    )
                }

                "screenshots" -> {
                    val selection: String
                    val args: Array<String>
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        selection = "(${android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR ${android.provider.MediaStore.Images.Media.RELATIVE_PATH} LIKE ?)"
                        args = arrayOf("%Screenshot%", "%Screenshots%")
                    } else {
                        selection = "${android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
                        args = arrayOf("%Screenshot%")
                    }
                    startActivity(
                        MediaQueryActivity.intent(
                            this,
                            title = getString(R.string.screenshots_cleanup),
                            selection = selection,
                            selectionArgs = args,
                            sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
                        )
                    )
                }

                "large" -> {
                    startActivity(
                        MediaQueryActivity.intent(
                            this,
                            title = getString(R.string.large_files),
                            selection = null,
                            selectionArgs = null,
                            sortOrder = "${android.provider.MediaStore.Images.Media.SIZE} DESC"
                        )
                    )
                }

                "oldest" -> startActivity(Intent(this, OldestMonthsActivity::class.java))
                "duplicates" -> startActivity(Intent(this, SimilarDuplicatesActivity::class.java))
                "blurry" -> startActivity(Intent(this, BlurryCandidatesActivity::class.java))
                "bursts" -> startActivity(Intent(this, BurstDetectionActivity::class.java))
                "events" -> startActivity(Intent(this, EventsActivity::class.java))
            }
        }

        featureTiles.layoutManager = GridLayoutManager(this, 2)
        featureTiles.adapter = featureTileAdapter
        featureTileAdapter.submitList(
            listOf(
                FeatureTile(
                    id = "whatsapp",
                    title = getString(R.string.whatsapp_cleanup),
                    subtitle = getString(R.string.tile_subtitle_whatsapp),
                    iconRes = R.drawable.ic_tile_whatsapp,
                    backgroundColorRes = R.color.feature_whatsapp
                ),
                FeatureTile(
                    id = "screenshots",
                    title = getString(R.string.screenshots_cleanup),
                    subtitle = getString(R.string.tile_subtitle_screenshots),
                    iconRes = R.drawable.ic_tile_screenshot,
                    backgroundColorRes = R.color.feature_screenshots
                ),
                FeatureTile(
                    id = "large",
                    title = getString(R.string.large_files),
                    subtitle = getString(R.string.tile_subtitle_large),
                    iconRes = R.drawable.ic_tile_storage,
                    backgroundColorRes = R.color.feature_large
                ),
                FeatureTile(
                    id = "oldest",
                    title = getString(R.string.oldest_files),
                    subtitle = getString(R.string.tile_subtitle_oldest),
                    iconRes = R.drawable.ic_tile_clock,
                    backgroundColorRes = R.color.feature_oldest
                ),
                FeatureTile(
                    id = "duplicates",
                    title = getString(R.string.similar_duplicates),
                    subtitle = getString(R.string.tile_subtitle_duplicates),
                    iconRes = R.drawable.ic_tile_duplicates,
                    backgroundColorRes = R.color.feature_duplicates
                ),
                FeatureTile(
                    id = "blurry",
                    title = getString(R.string.blurry_candidates),
                    subtitle = getString(R.string.tile_subtitle_blurry),
                    iconRes = R.drawable.ic_tile_blur,
                    backgroundColorRes = R.color.feature_blurry
                ),
                FeatureTile(
                    id = "bursts",
                    title = getString(R.string.bursts),
                    subtitle = getString(R.string.tile_subtitle_bursts),
                    iconRes = R.drawable.ic_tile_burst,
                    backgroundColorRes = R.color.feature_bursts
                ),
                FeatureTile(
                    id = "events",
                    title = getString(R.string.events),
                    subtitle = getString(R.string.tile_subtitle_events),
                    iconRes = R.drawable.ic_tile_event,
                    backgroundColorRes = R.color.feature_events
                )
            )
        )

        lastScanText.text = loadLastScanLabel() ?: getString(R.string.last_scan_placeholder)

        settingsButton.setOnClickListener {
            startActivity(SettingsActivity.intent(this))
        }

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
                val dashboard = withContext(Dispatchers.IO) { repository.scanDashboard() }
                totalImagesText.fadeToText(getString(R.string.total_images_value, dashboard.totalCount))
                totalSpaceText.fadeToText(getString(R.string.total_size_value, Formatters.bytesToHuman(dashboard.totalBytes)))
                bucketAdapter.submitList(dashboard.buckets)

                val label = storeLastScanNowAndGetLabel()
                lastScanText.fadeToText(getString(R.string.last_scan_value, label))
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

    private fun storeLastScanNowAndGetLabel(): String {
        val now = System.currentTimeMillis()
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SCAN_MS, now)
            .apply()
        return formatTime(now)
    }

    private fun loadLastScanLabel(): String? {
        val ms = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_LAST_SCAN_MS, -1L)
        if (ms <= 0L) return null
        return formatTime(ms)
    }

    private fun formatTime(ms: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(ms)
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

    companion object {
        private const val PREFS = "scan_prefs"
        private const val KEY_LAST_SCAN_MS = "last_scan_ms"
    }
}
