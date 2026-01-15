package com.example.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class AnalysisCacheDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    data class Row(
        val id: Long,
        val sizeBytes: Long,
        val dateTakenMs: Long,
        val dHashBits: Long,
        val blurScore: Double,
        val updatedAtMs: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
              $COL_ID INTEGER PRIMARY KEY,
              $COL_SIZE INTEGER NOT NULL,
              $COL_DATE_TAKEN INTEGER NOT NULL,
              $COL_DHASH INTEGER NOT NULL,
              $COL_BLUR REAL NOT NULL,
              $COL_UPDATED_AT INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_updated_at ON $TABLE($COL_UPDATED_AT);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Keep simple for now.
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun getByIds(ids: LongArray): Map<Long, Row> {
        if (ids.isEmpty()) return emptyMap()
        val map = HashMap<Long, Row>(ids.size)

        val maxChunk = 800
        var start = 0
        while (start < ids.size) {
            val end = (start + maxChunk).coerceAtMost(ids.size)
            val chunk = ids.copyOfRange(start, end)

            val placeholders = chunk.joinToString(",") { "?" }
            val args = chunk.map { it.toString() }.toTypedArray()

            readableDatabase.query(
                TABLE,
                arrayOf(COL_ID, COL_SIZE, COL_DATE_TAKEN, COL_DHASH, COL_BLUR, COL_UPDATED_AT),
                "$COL_ID IN ($placeholders)",
                args,
                null,
                null,
                null
            ).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(COL_ID)
                val sizeCol = cursor.getColumnIndexOrThrow(COL_SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(COL_DATE_TAKEN)
                val dhashCol = cursor.getColumnIndexOrThrow(COL_DHASH)
                val blurCol = cursor.getColumnIndexOrThrow(COL_BLUR)
                val updCol = cursor.getColumnIndexOrThrow(COL_UPDATED_AT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    map[id] = Row(
                        id = id,
                        sizeBytes = cursor.getLong(sizeCol),
                        dateTakenMs = cursor.getLong(dateCol),
                        dHashBits = cursor.getLong(dhashCol),
                        blurScore = cursor.getDouble(blurCol),
                        updatedAtMs = cursor.getLong(updCol)
                    )
                }
            }

            start = end
        }

        return map
    }

    fun upsert(rows: List<Row>) {
        if (rows.isEmpty()) return

        val db = writableDatabase
        db.beginTransaction()
        try {
            for (row in rows) {
                val values = ContentValues().apply {
                    put(COL_ID, row.id)
                    put(COL_SIZE, row.sizeBytes)
                    put(COL_DATE_TAKEN, row.dateTakenMs)
                    put(COL_DHASH, row.dHashBits)
                    put(COL_BLUR, row.blurScore)
                    put(COL_UPDATED_AT, row.updatedAtMs)
                }
                db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        private const val DB_NAME = "analysis_cache.db"
        private const val DB_VERSION = 1

        private const val TABLE = "analyzed_images"
        private const val COL_ID = "id"
        private const val COL_SIZE = "size_bytes"
        private const val COL_DATE_TAKEN = "date_taken_ms"
        private const val COL_DHASH = "dhash_bits"
        private const val COL_BLUR = "blur_score"
        private const val COL_UPDATED_AT = "updated_at_ms"
    }
}
