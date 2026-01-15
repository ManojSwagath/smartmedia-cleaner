package com.example.app

import kotlin.math.abs
import kotlin.math.ln

internal data class AnalyzedImage(
    val id: Long,
    val contentUri: android.net.Uri,
    val sizeBytes: Long,
    val dateTakenMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val dHash: ULong,
    val blurScore: Double
)

internal data class DuplicateCluster(
    val keep: AnalyzedImage,
    val items: List<AnalyzedImage>,
    val suggestedDeleteIds: LongArray
)

internal object DuplicateClustering {

    private fun log1p(x: Double): Double = ln(1.0 + x)

    /**
     * Simple LSH-ish candidate generation:
     * - Split 64-bit hash into 4 x 16-bit bands.
     * - Images sharing any band become candidates.
     */
    fun cluster(images: List<AnalyzedImage>, maxHamming: Int = 8): List<DuplicateCluster> {
        if (images.isEmpty()) return emptyList()

        val bandIndex = HashMap<Int, MutableList<Int>>(images.size * 2)

        fun bandKey(band: Int, value: Int): Int = (band shl 16) or (value and 0xFFFF)

        for ((idx, img) in images.withIndex()) {
            val h = img.dHash
            val b0 = (h and 0xFFFFu).toInt()
            val b1 = ((h shr 16) and 0xFFFFu).toInt()
            val b2 = ((h shr 32) and 0xFFFFu).toInt()
            val b3 = ((h shr 48) and 0xFFFFu).toInt()
            val keys = intArrayOf(
                bandKey(0, b0), bandKey(1, b1), bandKey(2, b2), bandKey(3, b3)
            )
            for (k in keys) {
                bandIndex.getOrPut(k) { ArrayList() }.add(idx)
            }
        }

        val visited = BooleanArray(images.size)
        val clusters = ArrayList<DuplicateCluster>()

        for (i in images.indices) {
            if (visited[i]) continue

            val seed = images[i]
            val candidateSet = linkedSetOf<Int>()

            val h = seed.dHash
            val keys = intArrayOf(
                bandKey(0, (h and 0xFFFFu).toInt()),
                bandKey(1, ((h shr 16) and 0xFFFFu).toInt()),
                bandKey(2, ((h shr 32) and 0xFFFFu).toInt()),
                bandKey(3, ((h shr 48) and 0xFFFFu).toInt())
            )

            for (k in keys) {
                val list = bandIndex[k] ?: continue
                for (idx in list) candidateSet.add(idx)
            }

            val groupIdx = ArrayList<Int>()
            for (idx in candidateSet) {
                if (visited[idx]) continue
                val other = images[idx]
                val dist = ImageAnalysis.hammingDistance(seed.dHash, other.dHash)

                // Cross-folder matches are more likely to be false positives; require closer match.
                val effectiveMax = if (seed.bucketId == other.bucketId) {
                    maxHamming
                } else {
                    (maxHamming - 2).coerceAtLeast(2)
                }

                // Extra gates to avoid “mostly white / blank” images clustering incorrectly.
                val sizeRatio = if (seed.sizeBytes <= 0L || other.sizeBytes <= 0L) 1.0 else {
                    val max = maxOf(seed.sizeBytes, other.sizeBytes).toDouble()
                    val min = minOf(seed.sizeBytes, other.sizeBytes).toDouble()
                    max / min
                }

                val blurDelta = abs(log1p(seed.blurScore) - log1p(other.blurScore))

                val passesSizeGate = sizeRatio <= 2.5 || dist <= 3
                val passesBlurGate = blurDelta <= 1.0 || dist <= 3

                if (dist <= effectiveMax && passesSizeGate && passesBlurGate) {
                    groupIdx.add(idx)
                }
            }

            if (groupIdx.size >= 2) {
                for (idx in groupIdx) visited[idx] = true

                val group = groupIdx.map { images[it] }

                // Keep the “best” = largest file (often best quality), tie-break newest.
                val keep = group.maxWith(compareBy<AnalyzedImage> { it.sizeBytes }.thenByDescending { it.dateTakenMs })
                val suggestedDeleteIds = group.asSequence().filter { it.id != keep.id }.map { it.id }.toList().toLongArray()
                clusters.add(DuplicateCluster(keep = keep, items = group, suggestedDeleteIds = suggestedDeleteIds))
            }
        }

        return clusters.sortedByDescending { it.items.size }
    }
}
