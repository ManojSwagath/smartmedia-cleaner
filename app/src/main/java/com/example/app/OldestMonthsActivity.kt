package com.example.app

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale

class OldestMonthsActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var listRecycler: RecyclerView

    private lateinit var repository: MediaStoreRepository
    private lateinit var adapter: MonthGroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_list)

        repository = MediaStoreRepository(this)

        titleText = findViewById(R.id.titleText)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        listRecycler = findViewById(R.id.listRecycler)

        titleText.text = getString(R.string.oldest_files)
        statusText.text = getString(R.string.analyzing)
        progressBar.isIndeterminate = true

        adapter = MonthGroupAdapter { group ->
            startActivity(
                IdsGridActivity.intent(
                    this,
                    title = group.title,
                    ids = group.ids
                )
            )
        }

        listRecycler.layoutManager = LinearLayoutManager(this)
        listRecycler.adapter = adapter

        analyze()
    }

    private fun analyze() {
        lifecycleScope.launch {
            try {
                val basics = withContext(Dispatchers.IO) {
                    val limit = AppSettings.applyAnalysisLimit(this@OldestMonthsActivity, defaultLimit = 12000)
                    repository.listAllImagesBasic(limit = limit, sortOrder = "${android.provider.MediaStore.Images.Media.DATE_TAKEN} ASC")
                }

                if (basics.isEmpty()) {
                    statusText.text = getString(R.string.nothing_found)
                    progressBar.visibility = View.GONE
                    return@launch
                }

                val groups = withContext(Dispatchers.Default) { groupByMonth(basics) }

                progressBar.visibility = View.GONE
                statusText.text = if (groups.isEmpty()) getString(R.string.nothing_found) else "${groups.size} months"
                adapter.submitList(groups)

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@OldestMonthsActivity, getString(R.string.scan_failed, e.message ?: "error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun groupByMonth(basics: List<MediaStoreRepository.MediaItemBasic>): List<MonthGroup> {
        val filtered = basics.filter { it.dateTakenMs > 0L }
        if (filtered.isEmpty()) return emptyList()

        val cal = Calendar.getInstance()

        data class Key(val year: Int, val month: Int)

        val map = linkedMapOf<Key, MutableList<MediaStoreRepository.MediaItemBasic>>()
        for (item in filtered) {
            cal.timeInMillis = item.dateTakenMs
            val key = Key(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            map.getOrPut(key) { ArrayList() }.add(item)
        }

        val monthNameFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        fun labelFor(year: Int, month: Int): String {
            cal.clear()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            // Example: "Jan 2026" depending on locale.
            val raw = monthNameFormat.format(cal.time)
            // Many locales include the day; keep it simple by using year-month in subtitle via raw.
            return raw
        }

        val groups = map.entries
            .sortedWith(compareBy<Map.Entry<Key, MutableList<MediaStoreRepository.MediaItemBasic>>> { it.key.year }.thenBy { it.key.month })
            .map { (key, items) ->
                val ids = items.map { it.id }.toLongArray()
                val totalBytes = items.sumOf { it.sizeBytes }
                val title = "${key.year}-${(key.month + 1).toString().padStart(2, '0')}"
                val displayTitle = "$title (${items.size})"
                MonthGroup(
                    title = displayTitle,
                    ids = ids,
                    count = items.size,
                    totalBytes = totalBytes,
                    previewId = ids.firstOrNull()
                )
            }

        // Oldest first.
        return groups
    }
}

internal data class MonthGroup(
    val title: String,
    val ids: LongArray,
    val count: Int,
    val totalBytes: Long,
    val previewId: Long?
)
