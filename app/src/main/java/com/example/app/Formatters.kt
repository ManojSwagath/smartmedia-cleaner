package com.example.app

import java.text.DecimalFormat
import java.util.Locale

internal object Formatters {
    private val decimalFormat = DecimalFormat("#.##")

    fun bytesToHuman(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val unit = 1024.0
        val kb = bytes / unit
        val mb = kb / unit
        val gb = mb / unit
        return when {
            gb >= 1 -> "${decimalFormat.format(gb)} GB"
            mb >= 1 -> "${decimalFormat.format(mb)} MB"
            kb >= 1 -> "${decimalFormat.format(kb)} KB"
            else -> String.format(Locale.US, "%d B", bytes)
        }
    }
}
