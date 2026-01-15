package com.example.app

import android.graphics.Bitmap
import kotlin.math.abs

internal object ImageAnalysis {

    /**
     * 64-bit dHash (difference hash). Similar images tend to have low Hamming distance.
     */
    fun dHash64(bitmap: Bitmap): ULong {
        // Resize to 9x8 so we can compare adjacent pixels in each row.
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash: ULong = 0u
        var bitIndex = 0

        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = scaled.getPixel(x, y)
                val right = scaled.getPixel(x + 1, y)

                val leftLuma = luma(left)
                val rightLuma = luma(right)

                if (leftLuma > rightLuma) {
                    hash = hash or (1uL shl bitIndex)
                }
                bitIndex++
            }
        }

        if (scaled !== bitmap) scaled.recycle()
        return hash
    }

    fun hammingDistance(a: ULong, b: ULong): Int {
        var x = a xor b
        var count = 0
        while (x != 0uL) {
            x = x and (x - 1uL)
            count++
        }
        return count
    }

    /**
     * Blur score via variance of Laplacian on a small grayscale representation.
     * Higher score => sharper. Lower score => blurrier.
     */
    fun blurScore(bitmap: Bitmap): Double {
        val w = 64
        val h = 64
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)

        val gray = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = scaled.getPixel(x, y)
                gray[y * w + x] = luma(p)
            }
        }

        // 3x3 Laplacian kernel: [0 1 0; 1 -4 1; 0 1 0]
        val values = DoubleArray((w - 2) * (h - 2))
        var idx = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = gray[y * w + x]
                val up = gray[(y - 1) * w + x]
                val down = gray[(y + 1) * w + x]
                val left = gray[y * w + (x - 1)]
                val right = gray[y * w + (x + 1)]

                val lap = (up + down + left + right) - 4 * c
                values[idx++] = abs(lap).toDouble()
            }
        }

        // Variance
        val mean = values.average()
        var varSum = 0.0
        for (v in values) {
            val d = v - mean
            varSum += d * d
        }

        if (scaled !== bitmap) scaled.recycle()
        return varSum / values.size.coerceAtLeast(1)
    }

    private fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = (argb) and 0xFF
        // Integer approximation of Rec.601
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
