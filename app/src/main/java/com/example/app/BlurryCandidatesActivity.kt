package com.example.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlurryCandidatesActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var actionButton: Button
    private lateinit var controlsText: TextView
    private lateinit var controlsSeekBar: SeekBar

    private lateinit var repository: MediaStoreRepository
    private lateinit var cacheDb: AnalysisCacheDb

    private var analyzed: List<AnalyzedImage> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_list)

        repository = MediaStoreRepository(this)
        cacheDb = AnalysisCacheDb(this)

        titleText = findViewById(R.id.titleText)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        actionButton = findViewById(R.id.actionButton)
        controlsText = findViewById(R.id.controlsText)
        controlsSeekBar = findViewById(R.id.controlsSeekBar)

        // Hide list for now; we jump directly to the review grid.
        findViewById<View>(R.id.listRecycler).visibility = View.GONE

        titleText.text = getString(R.string.blurry_candidates)
        statusText.text = getString(R.string.analyzing)
        progressBar.isIndeterminate = true

        actionButton.visibility = View.GONE
        controlsText.visibility = View.GONE
        controlsSeekBar.visibility = View.GONE

        analyze()
    }

    override fun onDestroy() {
        super.onDestroy()
        cacheDb.close()
    }

    private fun analyze() {
        lifecycleScope.launch {
            try {
                val basics = withContext(Dispatchers.IO) {
                    val limit = AppSettings.applyAnalysisLimit(this@BlurryCandidatesActivity, defaultLimit = 2500)
                    repository.listAllImagesBasic(limit = limit)
                }

                // Filter out common “messaging/download/document scan” buckets by default.
                // This keeps the Blurry screen more useful (camera-like photos).
                val filteredBasics = basics.filter { !isExcludedBucket(it.bucketName) }

                if (filteredBasics.isEmpty()) {
                    statusText.text = getString(R.string.nothing_found)
                    progressBar.visibility = View.GONE
                    return@launch
                }

                progressBar.isIndeterminate = false
                progressBar.max = filteredBasics.size

                analyzed = AnalysisPipeline.analyze(
                    context = this@BlurryCandidatesActivity,
                    cacheDb = cacheDb,
                    basics = filteredBasics
                ) { done, total ->
                    progressBar.progress = done
                    statusText.text = getString(R.string.analysis_progress, done, total)
                }

                progressBar.visibility = View.GONE

                if (analyzed.isEmpty()) {
                    statusText.text = getString(R.string.nothing_found)
                    return@launch
                }

                setupControlsAndWait()

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@BlurryCandidatesActivity, getString(R.string.scan_failed, e.message ?: "error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupControlsAndWait() {
        statusText.text = getString(R.string.blur_sensitivity)

        val sortedScores = analyzed.map { it.blurScore }.sorted()
        val minScore = sortedScores.firstOrNull() ?: 0.0
        val maxScore = sortedScores.lastOrNull() ?: minScore

        fun thresholdForProgress(progress: Int): Double {
            if (maxScore <= minScore) return minScore
            val t = (progress.coerceIn(0, 100)) / 100.0
            return minScore + (maxScore - minScore) * t
        }

        fun updateUi(progress: Int) {
            val threshold = thresholdForProgress(progress)
            controlsText.text = getString(R.string.blur_threshold_value, String.format("%.1f", threshold))

            val count = analyzed.count { it.blurScore <= threshold }
            actionButton.text = getString(R.string.review_candidates) + " (" + count + ")"
            actionButton.isEnabled = count > 0
        }

        controlsText.visibility = View.VISIBLE
        controlsSeekBar.visibility = View.VISIBLE
        actionButton.visibility = View.VISIBLE

        // Slightly less aggressive default so users typically see more than a handful of items.
        controlsSeekBar.progress = 50
        updateUi(controlsSeekBar.progress)

        controlsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateUi(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        actionButton.setOnClickListener {
            val threshold = thresholdForProgress(controlsSeekBar.progress)
            val candidates = analyzed
                .filter { it.blurScore <= threshold }
                .sortedBy { it.blurScore }
                .take(1200)

            val ids = candidates.map { it.id }.toLongArray()
            if (ids.isEmpty()) {
                Toast.makeText(this, getString(R.string.nothing_found), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startActivity(
                IdsGridActivity.intent(
                    this@BlurryCandidatesActivity,
                    title = getString(R.string.blurry_candidates),
                    ids = ids,
                    suggestedDeleteIds = ids
                )
            )
            finish()
        }
    }

    private fun isExcludedBucket(bucketName: String): Boolean {
        val name = bucketName.lowercase()
        val excludes = listOf(
            "whatsapp",
            "screenshots",
            "screen shots",
            "download",
            "downloads",
            "document",
            "documents",
            "scanner",
            "camscanner",
            "office lens",
            "telegram",
            "instagram",
            "facebook",
            "messenger",
            "snapchat",
            "picsart"
        )
        return excludes.any { name.contains(it) }
    }
}
