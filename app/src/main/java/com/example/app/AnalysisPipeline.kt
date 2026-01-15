package com.example.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AnalysisPipeline {

    suspend fun analyze(
        context: android.content.Context,
        cacheDb: AnalysisCacheDb,
        basics: List<MediaStoreRepository.MediaItemBasic>,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<AnalyzedImage> {
        if (basics.isEmpty()) return emptyList()

        val ids = basics.map { it.id }.toLongArray()
        val cached = withContext(Dispatchers.IO) { cacheDb.getByIds(ids) }

        val toUpsert = ArrayList<AnalysisCacheDb.Row>(128)
        val analyzed = ArrayList<AnalyzedImage>(basics.size)

        for ((idx, item) in basics.withIndex()) {
            val cachedRow = cached[item.id]
            val useCached = cachedRow != null &&
                cachedRow.sizeBytes == item.sizeBytes &&
                cachedRow.dateTakenMs == item.dateTakenMs

            if (useCached) {
                analyzed.add(
                    AnalyzedImage(
                        id = item.id,
                        contentUri = item.contentUri,
                        sizeBytes = item.sizeBytes,
                        dateTakenMs = item.dateTakenMs,
                        bucketId = item.bucketId,
                        bucketName = item.bucketName,
                        dHash = cachedRow!!.dHashBits.toULong(),
                        blurScore = cachedRow.blurScore
                    )
                )
            } else {
                val thumb = ThumbnailLoader.load(context, item.contentUri, sizePx = 128)
                if (thumb != null) {
                    val hash = ImageAnalysis.dHash64(thumb)
                    val blur = ImageAnalysis.blurScore(thumb)

                    analyzed.add(
                        AnalyzedImage(
                            id = item.id,
                            contentUri = item.contentUri,
                            sizeBytes = item.sizeBytes,
                            dateTakenMs = item.dateTakenMs,
                            bucketId = item.bucketId,
                            bucketName = item.bucketName,
                            dHash = hash,
                            blurScore = blur
                        )
                    )

                    toUpsert.add(
                        AnalysisCacheDb.Row(
                            id = item.id,
                            sizeBytes = item.sizeBytes,
                            dateTakenMs = item.dateTakenMs,
                            dHashBits = hash.toLong(),
                            blurScore = blur,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    )
                    thumb.recycle()
                }
            }

            if (idx % 25 == 0) {
                withContext(Dispatchers.Main) {
                    onProgress(idx + 1, basics.size)
                }
            }
        }

        withContext(Dispatchers.IO) { cacheDb.upsert(toUpsert) }

        withContext(Dispatchers.Main) {
            onProgress(basics.size, basics.size)
        }

        return analyzed
    }
}
