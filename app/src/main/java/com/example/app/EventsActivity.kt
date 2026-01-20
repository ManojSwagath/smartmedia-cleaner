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
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat

class EventsActivity : AppCompatActivity() {

    private lateinit var topAppBar: MaterialToolbar
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var listRecycler: RecyclerView
    private lateinit var emptyStateCard: View

    private lateinit var repository: MediaStoreRepository
    private lateinit var adapter: EventsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_list)

        repository = MediaStoreRepository(this)

        topAppBar = findViewById(R.id.topAppBar)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        listRecycler = findViewById(R.id.listRecycler)
        emptyStateCard = findViewById(R.id.emptyStateCard)

        topAppBar.title = getString(R.string.events)
        topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        statusText.text = getString(R.string.analyzing)
        progressBar.isIndeterminate = true

        adapter = EventsAdapter { event ->
            startActivity(
                IdsGridActivity.intent(
                    this,
                    title = event.title,
                    ids = event.ids
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
                    val limit = AppSettings.applyAnalysisLimit(this@EventsActivity, defaultLimit = 6000)
                    repository.listAllImagesBasic(limit = limit)
                }
                if (basics.isEmpty()) {
                    statusText.text = getString(R.string.nothing_found)
                    progressBar.visibility = View.GONE
                    emptyStateCard.visibility = View.VISIBLE
                    listRecycler.visibility = View.GONE
                    return@launch
                }

                val events = withContext(Dispatchers.Default) {
                    EventsGrouping.group(basics)
                }

                progressBar.visibility = View.GONE
                val isEmpty = events.isEmpty()
                emptyStateCard.visibility = if (isEmpty) View.VISIBLE else View.GONE
                listRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
                statusText.text = if (isEmpty) getString(R.string.nothing_found) else "${events.size} events"
                adapter.submitList(events)

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@EventsActivity, getString(R.string.scan_failed, e.message ?: "error"), Toast.LENGTH_LONG).show()
            }
        }
    }
}
