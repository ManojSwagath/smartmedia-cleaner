package com.example.app

internal data class BurstCluster(
    val keep: AnalyzedImage,
    val items: List<AnalyzedImage>,
    val suggestedDeleteIds: LongArray
)

internal object BurstGrouping {

    /**
     * Groups likely burst shots by:
     * - same bucket (album)
     * - time gaps <= windowMs between consecutive items
     */
    fun group(
        images: List<AnalyzedImage>,
        windowMs: Long = 2000L,
        minItems: Int = 3
    ): List<BurstCluster> {
        if (images.isEmpty()) return emptyList()

        val sorted = images
            .filter { it.dateTakenMs > 0L }
            .sortedByDescending { it.dateTakenMs }

        val clusters = ArrayList<BurstCluster>()
        val current = ArrayList<AnalyzedImage>()

        fun flush() {
            if (current.size >= minItems) {
                // Keep the sharpest, tie-break largest.
                val keep = current.maxWith(compareBy<AnalyzedImage> { it.blurScore }.thenBy { it.sizeBytes })
                val suggested = current.asSequence().filter { it.id != keep.id }.map { it.id }.toList().toLongArray()
                clusters.add(BurstCluster(keep = keep, items = current.toList(), suggestedDeleteIds = suggested))
            }
            current.clear()
        }

        for (img in sorted) {
            if (current.isEmpty()) {
                current.add(img)
                continue
            }

            val prev = current.last()
            val sameBucket = prev.bucketId == img.bucketId
            val closeInTime = (prev.dateTakenMs - img.dateTakenMs) <= windowMs

            if (sameBucket && closeInTime) {
                current.add(img)
            } else {
                flush()
                current.add(img)
            }
        }

        flush()

        return clusters.sortedByDescending { it.items.size }
    }
}
