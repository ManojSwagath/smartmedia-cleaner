package com.example.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackgroundIndexWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!AppSettings.isBackgroundIndexingEnabled(applicationContext)) {
            persistStatus(success = true, message = "Disabled")
            return Result.success()
        }

        // If permission isn't granted, we can't scan. Don't spam retries.
        if (!hasReadPermission()) {
            persistStatus(success = false, message = "No permission")
            return Result.success()
        }

        return try {
            val repo = MediaStoreRepository(applicationContext)
            val dashboard = repo.scanDashboard()

            // Warm a small portion of the analysis cache so "Similar/Blurry/Bursts" feel faster.
            val cacheDb = AnalysisCacheDb(applicationContext)
            try {
                val limit = AppSettings.applyBackgroundWarmLimit(applicationContext, defaultLimit = 800)
                val basics = repo.listAllImagesBasic(limit = limit)
                AnalysisPipeline.analyze(
                    context = applicationContext,
                    cacheDb = cacheDb,
                    basics = basics,
                    onProgress = { _, _ -> }
                )
            } finally {
                cacheDb.close()
            }

            persistDashboard(dashboard)
            persistStatus(success = true, message = "OK")
            Result.success()
        } catch (e: Exception) {
            persistStatus(success = false, message = e.message ?: "error")
            // Retry later with backoff.
            Result.retry()
        }
    }

    private fun hasReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return ContextCompat.checkSelfPermission(applicationContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun persistDashboard(dashboard: MediaStoreRepository.ScanDashboard) {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_BG_TOTAL_COUNT, dashboard.totalCount)
            .putLong(KEY_BG_TOTAL_BYTES, dashboard.totalBytes)
            .putLong(KEY_BG_LAST_RUN_MS, System.currentTimeMillis())
            .apply()
    }

    private fun persistStatus(success: Boolean, message: String) {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_BG_LAST_SUCCESS, success)
            .putString(KEY_BG_LAST_MESSAGE, message)
            .apply()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "background_media_index"

        private const val PREFS = "background_index"
        private const val KEY_BG_TOTAL_COUNT = "bg_total_count"
        private const val KEY_BG_TOTAL_BYTES = "bg_total_bytes"
        private const val KEY_BG_LAST_RUN_MS = "bg_last_run_ms"
        private const val KEY_BG_LAST_SUCCESS = "bg_last_success"
        private const val KEY_BG_LAST_MESSAGE = "bg_last_message"
    }
}
