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
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaQueryActivity : AppCompatActivity() {

    private lateinit var topAppBar: MaterialToolbar
    private lateinit var selectedText: TextView
    private lateinit var deleteButton: Button
    private lateinit var selectAllButton: Button
    private lateinit var mediaRecycler: RecyclerView
    private lateinit var emptyStateCard: android.view.View

    private lateinit var repository: MediaStoreRepository
    private lateinit var adapter: MediaAdapter

    private var currentItems: List<MediaStoreRepository.MediaItem> = emptyList()

    private var queryTitle: String = ""
    private var selection: String? = null
    private var selectionArgs: Array<String>? = null
    private var sortOrder: String? = null

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

        queryTitle = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.folder_detail_title)
        selection = intent.getStringExtra(EXTRA_SELECTION)
        selectionArgs = intent.getStringArrayExtra(EXTRA_SELECTION_ARGS)
        sortOrder = intent.getStringExtra(EXTRA_SORT_ORDER)

        topAppBar = findViewById(R.id.topAppBar)
        selectedText = findViewById(R.id.selectedText)
        deleteButton = findViewById(R.id.deleteButton)
        selectAllButton = findViewById(R.id.selectAllButton)
        mediaRecycler = findViewById(R.id.mediaRecycler)
        emptyStateCard = findViewById(R.id.emptyStateCard)

        topAppBar.title = queryTitle
        topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = MediaAdapter(
            onSelectionChanged = { selectedCount ->
                selectedText.text = getString(R.string.selected_count_value, selectedCount)
                deleteButton.isEnabled = selectedCount > 0
            },
            onPreviewRequested = { item ->
                val ids = currentItems.map { it.id }.toLongArray()
                val startIndex = currentItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                startActivity(
                    PreviewActivity.intentForIds(
                        this,
                        ids = ids,
                        startIndex = startIndex,
                        title = queryTitle
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

        deleteButton.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            animatePopOffSelected(
                recyclerView = mediaRecycler,
                selectedIds = adapter.getSelectedIdsSnapshot(),
                idAtPosition = { pos -> adapter.idAtPosition(pos) }
            )
            requestDelete(selected.map { it.contentUri })
        }

        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    repository.queryImages(
                        selection = selection,
                        selectionArgs = selectionArgs,
                        sortOrder = sortOrder
                    )
                }
                currentItems = items
                adapter.submitList(items)
                val isEmpty = items.isEmpty()
                emptyStateCard.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
                mediaRecycler.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
                selectAllButton.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
                deleteButton.isEnabled = false
                selectedText.text = getString(R.string.selected_count_value, 0)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MediaQueryActivity,
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
                    Toast.makeText(this@MediaQueryActivity, messageRes, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        this@MediaQueryActivity,
                        getString(R.string.delete_failed_with_message, e.message ?: "error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SELECTION = "extra_selection"
        private const val EXTRA_SELECTION_ARGS = "extra_selection_args"
        private const val EXTRA_SORT_ORDER = "extra_sort_order"

        fun intent(
            context: android.content.Context,
            title: String,
            selection: String? = null,
            selectionArgs: Array<String>? = null,
            sortOrder: String? = null
        ): Intent {
            return Intent(context, MediaQueryActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SELECTION, selection)
                putExtra(EXTRA_SELECTION_ARGS, selectionArgs)
                putExtra(EXTRA_SORT_ORDER, sortOrder)
            }
        }
    }
}
