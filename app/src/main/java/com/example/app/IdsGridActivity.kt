package com.example.app

import android.app.Activity
import android.app.RecoverableSecurityException
import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IdsGridActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var selectedText: TextView
    private lateinit var selectSuggestedButton: Button
    private lateinit var selectAllButton: Button
    private lateinit var deleteButton: Button
    private lateinit var mediaRecycler: RecyclerView

    private lateinit var repository: MediaStoreRepository
    private lateinit var adapter: MediaAdapter

    private var currentItems: List<MediaStoreRepository.MediaItem> = emptyList()

    private var title: String = ""
    private var ids: LongArray = longArrayOf()
    private var suggestedDeleteIds: LongArray = longArrayOf()

    private var pendingLegacyDeleteUris: List<android.net.Uri> = emptyList()

    private val legacyWritePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val toDelete = pendingLegacyDeleteUris
            pendingLegacyDeleteUris = emptyList()
            if (toDelete.isNotEmpty()) requestDelete(toDelete)
        } else {
            pendingLegacyDeleteUris = emptyList()
            Toast.makeText(this, R.string.delete_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refresh()
        } else {
            Toast.makeText(this, R.string.delete_cancelled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_detail)

        repository = MediaStoreRepository(this)

        title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.folder_detail_title)
        ids = intent.getLongArrayExtra(EXTRA_IDS) ?: longArrayOf()
        suggestedDeleteIds = intent.getLongArrayExtra(EXTRA_SUGGESTED_DELETE_IDS) ?: longArrayOf()

        titleText = findViewById(R.id.folderTitle)
        selectedText = findViewById(R.id.selectedText)
        selectSuggestedButton = findViewById(R.id.selectSuggestedButton)
        selectAllButton = findViewById(R.id.selectAllButton)
        deleteButton = findViewById(R.id.deleteButton)
        mediaRecycler = findViewById(R.id.mediaRecycler)

        val screenTitle = title.toString()
        titleText.text = screenTitle

        adapter = MediaAdapter(
            onSelectionChanged = { selectedCount ->
                selectedText.text = getString(R.string.selected_count_value, selectedCount)
                deleteButton.isEnabled = selectedCount > 0
            },
            onPreviewRequested = { item ->
                val idsForPager = currentItems.map { it.id }.toLongArray()
                val startIndex = currentItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                startActivity(
                    PreviewActivity.intentForIds(
                        this,
                        ids = idsForPager,
                        startIndex = startIndex,
                        title = screenTitle
                    )
                )
            }
        )

        mediaRecycler.layoutManager = GridLayoutManager(this, 3)
        mediaRecycler.adapter = adapter
        mediaRecycler.addOnItemTouchListener(DragSelectTouchListener(adapter))

        selectAllButton.setOnClickListener {
            val allIds = currentItems.map { it.id }.toSet()
            if (allIds.isNotEmpty()) adapter.setSelectedIds(allIds)
        }

        if (suggestedDeleteIds.isNotEmpty()) {
            selectSuggestedButton.visibility = View.VISIBLE
            selectSuggestedButton.setOnClickListener {
                adapter.setSelectedIds(suggestedDeleteIds.toSet())
            }
        } else {
            selectSuggestedButton.visibility = View.GONE
        }

        deleteButton.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            requestDelete(selected.map { it.contentUri })
        }

        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    repository.getMediaByIds(ids, sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC")
                }
                currentItems = items
                adapter.submitList(items, preselectedIds = suggestedDeleteIds.toSet())
                selectAllButton.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            } catch (e: Exception) {
                Toast.makeText(
                    this@IdsGridActivity,
                    getString(R.string.scan_failed, e.message ?: "error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun requestDelete(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                deleteLauncher.launch(request)
            } catch (_: SecurityException) {
                Toast.makeText(this, R.string.delete_permission_required, Toast.LENGTH_LONG).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, getString(R.string.delete_failed_with_message, e.message ?: "error"), Toast.LENGTH_LONG).show()
            }
            return
        }

        // Android 9 and below requires legacy storage write permission for direct deletes.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingLegacyDeleteUris = uris
                legacyWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    for (uri in uris) {
                        try {
                            contentResolver.delete(uri, null, null)
                        } catch (e: SecurityException) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                                val intentSender: IntentSender = e.userAction.actionIntent.intentSender
                                val request = IntentSenderRequest.Builder(intentSender).build()
                                withContext(Dispatchers.Main) {
                                    deleteLauncher.launch(request)
                                }
                                return@withContext
                            } else {
                                throw e
                            }
                        }
                    }
                }
                refresh()
            } catch (e: Exception) {
                val messageRes = if (e is SecurityException && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    R.string.delete_permission_required
                } else {
                    null
                }
                if (messageRes != null) {
                    Toast.makeText(this@IdsGridActivity, messageRes, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        this@IdsGridActivity,
                        getString(R.string.delete_failed_with_message, e.message ?: "error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_IDS = "extra_ids"
        private const val EXTRA_SUGGESTED_DELETE_IDS = "extra_suggested_delete_ids"

        fun intent(
            context: android.content.Context,
            title: String,
            ids: LongArray,
            suggestedDeleteIds: LongArray = longArrayOf()
        ): Intent {
            return Intent(context, IdsGridActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_IDS, ids)
                putExtra(EXTRA_SUGGESTED_DELETE_IDS, suggestedDeleteIds)
            }
        }
    }
}
