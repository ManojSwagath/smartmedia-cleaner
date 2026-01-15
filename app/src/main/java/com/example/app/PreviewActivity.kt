package com.example.app

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.viewpager2.widget.ViewPager2
import coil.load
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewActivity : AppCompatActivity() {

    private var selectedIds = linkedSetOf<Long>()

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Items were deleted by the system UI.
            onDeleteCompletedSuccess()
        } else {
            Toast.makeText(this, R.string.delete_cancelled, Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingDeleteIds: LongArray = longArrayOf()
    private var pendingLegacyDeleteUris: List<Uri> = emptyList()

    private val legacyWritePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val toDelete = pendingLegacyDeleteUris
            pendingLegacyDeleteUris = emptyList()
            if (toDelete.isNotEmpty()) {
                // Continue delete with permission granted.
                requestDeleteUris(toDelete)
            }
        } else {
            pendingLegacyDeleteUris = emptyList()
            Toast.makeText(this, R.string.delete_permission_required, Toast.LENGTH_LONG).show()
        }
    }
    private var pagerAdapter: PreviewPagerAdapter? = null
    private var pager: ViewPager2? = null
    private var selectedCountText: TextView? = null
    private var subtitleText: TextView? = null
    private var selectButton: Button? = null
    private var deleteButton: Button? = null

    private fun updatePagerSubtitle(position: Int) {
        val adapter = pagerAdapter ?: return
        val count = adapter.itemCount
        val view = subtitleText ?: return
        view.text = if (count <= 1) "" else "${position + 1} / $count"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val ids = intent.getLongArrayExtra(EXTRA_IDS)
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0).coerceAtLeast(0)
        val singleUri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        val titleText = findViewById<TextView>(R.id.titleText)
        subtitleText = findViewById(R.id.subtitleText)
        titleText.text = title

        pager = findViewById(R.id.previewPager)
        selectedCountText = findViewById(R.id.selectedCountText)
        selectButton = findViewById(R.id.selectButton)
        deleteButton = findViewById(R.id.deleteButton)

        val adapter = when {
            ids != null && ids.isNotEmpty() -> {
                PreviewPagerAdapter(
                    ids = ids,
                    onTap = { finish() }
                )
            }

            singleUri != null -> {
                PreviewPagerAdapter(
                    uris = arrayOf(singleUri),
                    onTap = { finish() }
                )
            }

            else -> {
                // Nothing to show; exit gracefully.
                finish()
                return
            }
        }

        pagerAdapter = adapter
        pager!!.adapter = adapter

        fun updateSubtitle(position: Int) {
            updatePagerSubtitle(position)
        }

        fun updateSelectionUi() {
            val count = selectedIds.size
            selectedCountText?.text = getString(R.string.selected_count_value, count)
            deleteButton?.isEnabled = count > 0

            val currentId = adapter.getIdAt(pager!!.currentItem)
            val isSelected = currentId != null && selectedIds.contains(currentId)
            selectButton?.text = if (isSelected) getString(R.string.selected) else getString(R.string.select)
        }

        pager!!.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateSubtitle(position)
                    updateSelectionUi()
                }
            }
        )

        pager!!.setCurrentItem(startIndex.coerceAtMost(adapter.itemCount - 1), false)
        updateSubtitle(pager!!.currentItem)

        // Controls
        selectButton?.setOnClickListener {
            val currentId = adapter.getIdAt(pager!!.currentItem)
            if (currentId != null) {
                if (selectedIds.contains(currentId)) selectedIds.remove(currentId) else selectedIds.add(currentId)
                updateSelectionUi()
            }
        }

        deleteButton?.setOnClickListener {
            val toDelete = selectedIds.toLongArray()
            if (toDelete.isEmpty()) return@setOnClickListener
            requestDeleteByIds(toDelete)
        }

        // Initial state
        deleteButton?.isEnabled = false
        updateSelectionUi()
    }

    private fun requestDeleteByIds(ids: LongArray) {
        if (ids.isEmpty()) return

        val uris = ids.map {
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it)
        }

        // Remember what we're trying to delete, so we can update the pager when the system UI returns.
        pendingDeleteIds = ids

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

        requestDeleteUris(uris)
    }

    private fun requestDeleteUris(uris: List<Uri>) {
        if (uris.isEmpty()) return

        // Android 9 and below requires legacy storage write permission for direct deletes.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingLegacyDeleteUris = uris
                legacyWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }

        // Best-effort for older devices.
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
                onDeleteCompletedSuccess()
            } catch (e: Exception) {
                val messageRes = if (e is SecurityException && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    R.string.delete_permission_required
                } else {
                    null
                }
                if (messageRes != null) {
                    Toast.makeText(this@PreviewActivity, messageRes, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        this@PreviewActivity,
                        getString(R.string.delete_failed_with_message, e.message ?: "error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun onDeleteCompletedSuccess() {
        val adapter = pagerAdapter ?: return
        val pagerView = pager ?: return

        val deleted = pendingDeleteIds.toSet()
        if (deleted.isEmpty()) return

        // Clear selection for deleted ids.
        selectedIds.removeAll(deleted)

        val beforePosition = pagerView.currentItem
        val currentId = adapter.getIdAt(beforePosition)

        adapter.removeIds(deleted)
        val afterCount = adapter.itemCount

        if (afterCount <= 0) {
            finish()
            return
        }

        // Try to keep a stable page after deletion.
        val newPosition = if (currentId != null) {
            val newIndex = adapter.indexOfId(currentId)
            if (newIndex >= 0) newIndex else beforePosition.coerceAtMost(afterCount - 1)
        } else {
            beforePosition.coerceAtMost(afterCount - 1)
        }

        pagerView.setCurrentItem(newPosition, false)
        updatePagerSubtitle(pagerView.currentItem)
        // Also update the title/subtitle selection UI.
        selectedCountText?.text = getString(R.string.selected_count_value, selectedIds.size)
        deleteButton?.isEnabled = selectedIds.isNotEmpty()
        selectButton?.text = if (adapter.getIdAt(newPosition)?.let { selectedIds.contains(it) } == true) {
            getString(R.string.selected)
        } else {
            getString(R.string.select)
        }
    }

    companion object {
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_IDS = "extra_ids"
        private const val EXTRA_START_INDEX = "extra_start_index"
        private const val EXTRA_TITLE = "extra_title"

        fun intent(
            context: android.content.Context,
            uri: Uri,
            title: String
        ): android.content.Intent {
            return android.content.Intent(context, PreviewActivity::class.java).apply {
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_TITLE, title)
            }
        }

        fun intentForIds(
            context: android.content.Context,
            ids: LongArray,
            startIndex: Int,
            title: String
        ): android.content.Intent {
            return android.content.Intent(context, PreviewActivity::class.java).apply {
                putExtra(EXTRA_IDS, ids)
                putExtra(EXTRA_START_INDEX, startIndex)
                putExtra(EXTRA_TITLE, title)
            }
        }
    }
}

private class PreviewPagerAdapter(
    private val ids: LongArray? = null,
    private val uris: Array<Uri>? = null,
    private val onTap: () -> Unit
) : RecyclerView.Adapter<PreviewPagerAdapter.VH>() {

    private val idList: MutableList<Long>? = ids?.toMutableList()
    private val uriList: MutableList<Uri>? = uris?.toMutableList()

    init {
        require((ids != null && ids.isNotEmpty()) || (uris != null && uris.isNotEmpty()))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_preview_page, parent, false)
        return VH(view, onTap)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = when {
            idList != null -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, idList[position])
            else -> uriList!![position]
        }
        holder.bind(uri)
    }

    override fun getItemCount(): Int = idList?.size ?: (uriList?.size ?: 0)

    fun getIdAt(position: Int): Long? {
        val list = idList ?: return null
        if (position !in 0 until list.size) return null
        return list[position]
    }

    fun indexOfId(id: Long): Int {
        val list = idList ?: return -1
        return list.indexOf(id)
    }

    fun removeIds(idsToRemove: Set<Long>) {
        val list = idList ?: return
        val before = list.size
        list.removeAll { idsToRemove.contains(it) }
        if (list.size != before) notifyDataSetChanged()
    }

    class VH(itemView: View, onTap: () -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.pageImage)

        init {
            image.setOnClickListener { onTap() }
        }

        fun bind(uri: Uri) {
            image.load(uri) { crossfade(true) }
        }
    }
}
