package com.example.app

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

internal class MediaStoreRepository(private val context: Context) {

    data class ScanDashboard(
        val totalCount: Int,
        val totalBytes: Long,
        val buckets: List<BucketSummary>
    )

    data class BucketSummary(
        val bucketId: Long,
        val name: String,
        val count: Int,
        val totalBytes: Long
    )

    data class MediaItem(
        val id: Long,
        val contentUri: Uri,
        val sizeBytes: Long
    )

    data class MediaItemBasic(
        val id: Long,
        val contentUri: Uri,
        val sizeBytes: Long,
        val dateTakenMs: Long,
        val bucketId: Long,
        val bucketName: String
    )

    fun scanDashboard(): ScanDashboard {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val bucketAgg = linkedMapOf<Long, MutableBucketAgg>()
        var totalCount = 0
        var totalBytes = 0L

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.BUCKET_ID} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                cursor.getLong(idCol)
                val size = cursor.getLong(sizeCol)
                val bucketId = cursor.getLong(bucketIdCol)
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"

                totalCount++
                totalBytes += size

                val agg = bucketAgg.getOrPut(bucketId) { MutableBucketAgg(bucketId, bucketName) }
                agg.count++
                agg.totalBytes += size
            }
        }

        val buckets = bucketAgg.values
            .map { BucketSummary(it.bucketId, it.name, it.count, it.totalBytes) }
            .sortedWith(compareByDescending<BucketSummary> { it.totalBytes }.thenBy { it.name.lowercase() })

        return ScanDashboard(
            totalCount = totalCount,
            totalBytes = totalBytes,
            buckets = buckets
        )
    }

    fun listAllImagesBasic(
        limit: Int = 0,
        sortOrder: String = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    ): List<MediaItemBasic> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val results = ArrayList<MediaItemBasic>(1024)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val size = cursor.getLong(sizeCol)
                val dateTakenRaw = cursor.getLong(dateCol)
                val dateAddedSec = cursor.getLong(dateAddedCol)
                val dateTaken = if (dateTakenRaw > 0L) dateTakenRaw else dateAddedSec * 1000L
                val bucketId = cursor.getLong(bucketIdCol)
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                results.add(
                    MediaItemBasic(
                        id = id,
                        contentUri = uri,
                        sizeBytes = size,
                        dateTakenMs = dateTaken,
                        bucketId = bucketId,
                        bucketName = bucketName
                    )
                )
                if (limit > 0 && results.size >= limit) break
            }
        }
        return results
    }

    fun getBucketMedia(bucketId: Long): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val args = arrayOf(bucketId.toString())

        val results = ArrayList<MediaItem>(256)

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val size = cursor.getLong(sizeCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                results.add(MediaItem(id = id, contentUri = uri, sizeBytes = size))
            }
        }

        return results
    }

    fun queryImages(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE
        )

        val results = ArrayList<MediaItem>(256)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val size = cursor.getLong(sizeCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                results.add(MediaItem(id = id, contentUri = uri, sizeBytes = size))
            }
        }

        return results
    }

    fun getMediaByIds(ids: LongArray, sortOrder: String? = null): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()

        // Chunk to avoid SQLite limits.
        val maxChunk = 800
        val results = ArrayList<MediaItem>(ids.size)
        var start = 0
        while (start < ids.size) {
            val end = (start + maxChunk).coerceAtMost(ids.size)
            val chunk = ids.copyOfRange(start, end)

            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.Images.Media._ID} IN ($placeholders)"
            val args = chunk.map { it.toString() }.toTypedArray()

            results.addAll(
                queryImages(selection = selection, selectionArgs = args, sortOrder = sortOrder)
            )

            start = end
        }
        return results
    }

    private data class MutableBucketAgg(
        val bucketId: Long,
        val name: String,
        var count: Int = 0,
        var totalBytes: Long = 0
    )
}
