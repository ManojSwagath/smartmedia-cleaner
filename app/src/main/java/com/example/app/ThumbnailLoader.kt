package com.example.app

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size

internal object ThumbnailLoader {

    /**
     * Loads a small bitmap for analysis only (hash/blur). Never full-res.
     */
    fun load(context: Context, uri: android.net.Uri, sizePx: Int = 128): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                loadPreQ(context.contentResolver, uri, sizePx)
            }
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun loadPreQ(resolver: ContentResolver, uri: android.net.Uri, target: Int): Bitmap? {
        // Best-effort: decode a downsampled bitmap from stream.
        val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsBounds) }

        if (optsBounds.outWidth <= 0 || optsBounds.outHeight <= 0) return null

        val sample = computeInSampleSize(optsBounds.outWidth, optsBounds.outHeight, target, target)
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        resolver.openInputStream(uri)?.use {
            return BitmapFactory.decodeStream(it, null, opts)
        }
        return null
    }

    private fun computeInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            var halfH = height / 2
            var halfW = width / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
