package com.example.app

import android.os.Bundle
import android.view.View
import android.widget.Button
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

class BurstDetectionActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var actionButton: Button
    private lateinit var listRecycler: RecyclerView

    private lateinit var repository: MediaStoreRepository
    private lateinit var cacheDb: AnalysisCacheDb
    private lateinit var adapter: BurstAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_list)

        repository = MediaStoreRepository(this)
        cacheDb = AnalysisCacheDb(this)

        titleText = findViewById(R.id.titleText)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        actionButton = findViewById(R.id.actionButton)
        listRecycler = findViewById(R.id.listRecycler)

        titleText.text = getString(R.string.bursts)
        statusText.text = getString(R.string.analyzing)
        progressBar.isIndeterminate = true
        actionButton.visibility = View.GONE

        adapter = BurstAdapter { cluster ->
            val ids = cluster.items.map { it.id }.toLongArray()
            startActivity(
                IdsGridActivity.intent(
                    this,
                    title = getString(R.string.bursts),
                    ids = ids,
                    suggestedDeleteIds = cluster.suggestedDeleteIds
                )
            )
        }

        listRecycler.layoutManager = LinearLayoutManager(this)
        listRecycler.adapter = adapter

        analyze()
    }

    override fun onDestroy() {
        super.onDestroy()
        cacheDb.close()
    }

    private fun analyze() {
        lifecycleScope.launch {
            try {
                val basics = withContext(Dispatchers.IO) {
                    val limit = AppSettings.applyAnalysisLimit(this@BurstDetectionActivity, defaultLimit = 4000)
                    repository.listAllImagesBasic(limit = limit)
                }

                if (basics.isEmpty()) {
                    statusText.text = getString(R.string.nothing_found)
                    progressBar.visibility = View.GONE
                    return@launch
                }

                progressBar.isIndeterminate = false
                progressBar.max = basics.size

                val analyzed = AnalysisPipeline.analyze(
                    context = this@BurstDetectionActivity,
                    cacheDb = cacheDb,
                    basics = basics
                ) { done, total ->
                    progressBar.progress = done
                    statusText.text = getString(R.string.analysis_progress, done, total)
                }

                val bursts = withContext(Dispatchers.Default) {
                    BurstGrouping.group(analyzed)
                }

                progressBar.visibility = View.GONE
                statusText.text = if (bursts.isEmpty()) getString(R.string.nothing_found) else "${bursts.size} bursts"
                adapter.submitList(bursts)

                val allCandidateIds = bursts
                    .flatMap { it.items }
                    .map { it.id }
                    .distinct()
                    .toLongArray()

                val allSuggestedIds = bursts
                    .flatMap { it.suggestedDeleteIds.asList() }
                    .distinct()
                    .toLongArray()

                if (allCandidateIds.isNotEmpty() && allSuggestedIds.isNotEmpty()) {
                    actionButton.visibility = View.VISIBLE
                    actionButton.text = getString(R.string.review_all_suggested)
                    actionButton.setOnClickListener {
                        startActivity(
                            IdsGridActivity.intent(
                                this@BurstDetectionActivity,
                                title = getString(R.string.bursts),
                                ids = allCandidateIds,
                                suggestedDeleteIds = allSuggestedIds
                            )
                        )
                    }
                } else {
                    actionButton.visibility = View.GONE
                }

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@BurstDetectionActivity, getString(R.string.scan_failed, e.message ?: "error"), Toast.LENGTH_LONG).show()
            }
        }
    }
}
