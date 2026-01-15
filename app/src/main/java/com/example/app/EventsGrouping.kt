package com.example.app

import java.text.DateFormat

internal data class EventGroup(
    val title: String,
    val ids: LongArray,
    val count: Int,
    val totalBytes: Long,
    val startMs: Long,
    val endMs: Long
)

internal object EventsGrouping {

    /**
     * Time-based event grouping (no ML):
     * - Sort by time
     * - Start a new event when the gap exceeds [gapMs]
     *
     * Later: can also consider location if reliably available.
     */
    fun group(
        basics: List<MediaStoreRepository.MediaItemBasic>,
        gapMs: Long = 2L * 60L * 60L * 1000L, // 2 hours
        minItems: Int = 10
    ): List<EventGroup> {
        if (basics.isEmpty()) return emptyList()

        val sorted = basics
            .filter { it.dateTakenMs > 0L }
            .sortedBy { it.dateTakenMs }

        val groups = ArrayList<EventGroup>()
        val current = ArrayList<MediaStoreRepository.MediaItemBasic>()

        fun flush() {
            if (current.size >= minItems) {
                val start = current.first().dateTakenMs
                val end = current.last().dateTakenMs
                val ids = current.map { it.id }.toLongArray()
                val totalBytes = current.sumOf { it.sizeBytes }
                val title = buildTitle(start, end, current.size)
                groups.add(
                    EventGroup(
                        title = title,
                        ids = ids,
                        count = current.size,
                        totalBytes = totalBytes,
                        startMs = start,
                        endMs = end
                    )
                )
            }
            current.clear()
        }

        for (item in sorted) {
            if (current.isEmpty()) {
                current.add(item)
                continue
            }
            val prev = current.last()
            val gap = item.dateTakenMs - prev.dateTakenMs
            if (gap <= gapMs) {
                current.add(item)
            } else {
                flush()
                current.add(item)
            }
        }
        flush()

        return groups.sortedByDescending { it.count }
    }

    private fun buildTitle(startMs: Long, endMs: Long, count: Int): String {
        val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        return "Event ($count) • ${df.format(startMs)} → ${df.format(endMs)}"
    }
}
