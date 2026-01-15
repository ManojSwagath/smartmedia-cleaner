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

class SimilarDuplicatesActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var actionButton: Button
    private lateinit var listRecycler: RecyclerView

    private lateinit var repository: MediaStoreRepository
    private lateinit var adapter: ClusterAdapter
    private lateinit var cacheDb: AnalysisCacheDb

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

        titleText.text = getString(R.string.similar_duplicates)
        statusText.text = getString(R.string.analyzing)
        progressBar.isIndeterminate = true
        actionButton.visibility = View.GONE

        adapter = ClusterAdapter { cluster ->
            val ids = cluster.items.map { it.id }.toLongArray()
            startActivity(
                IdsGridActivity.intent(
                    this,
                    title = getString(R.string.similar_duplicates),
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
                    // Keep initial version bounded for performance.
                    val limit = AppSettings.applyAnalysisLimit(this@SimilarDuplicatesActivity, defaultLimit = 3000)
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
                    context = this@SimilarDuplicatesActivity,
                    cacheDb = cacheDb,
                    basics = basics
                ) { done, total ->
                    progressBar.progress = done
                    statusText.text = getString(R.string.analysis_progress, done, total)
                }

                val clusters = withContext(Dispatchers.Default) {
                    // Stricter to reduce false positives like “blank/white” images clustering.
                    DuplicateClustering.cluster(analyzed, maxHamming = 6)
                }

                progressBar.visibility = View.GONE
                statusText.text = if (clusters.isEmpty()) getString(R.string.nothing_found) else "${clusters.size} clusters"
                adapter.submitList(clusters)

                val allCandidateIds = clusters
                    .flatMap { it.items }
                    .map { it.id }
                    .distinct()
                    .toLongArray()

                val allSuggestedIds = clusters
                    .flatMap { it.suggestedDeleteIds.asList() }
                    .distinct()
                    .toLongArray()

                if (allCandidateIds.isNotEmpty() && allSuggestedIds.isNotEmpty()) {
                    actionButton.visibility = View.VISIBLE
                    actionButton.text = getString(R.string.review_all_suggested)
                    actionButton.setOnClickListener {
                        startActivity(
                            IdsGridActivity.intent(
                                this@SimilarDuplicatesActivity,
                                title = getString(R.string.similar_duplicates),
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
                Toast.makeText(this@SimilarDuplicatesActivity, getString(R.string.scan_failed, e.message ?: "error"), Toast.LENGTH_LONG).show()
            }
        }
    }
}
