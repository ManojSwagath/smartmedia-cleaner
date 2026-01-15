package com.example.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load

internal class MediaAdapter(
    private val onSelectionChanged: (selectedCount: Int) -> Unit,
    private val onPreviewRequested: ((MediaStoreRepository.MediaItem) -> Unit)? = null
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    private val items = mutableListOf<MediaStoreRepository.MediaItem>()
    private val selectedIds = linkedSetOf<Long>()
    private val idToPosition = HashMap<Long, Int>(2048)

    private var selectionMode: Boolean = false

    fun isSelectionMode(): Boolean = selectionMode

    fun submitList(newItems: List<MediaStoreRepository.MediaItem>) {
        items.clear()
        items.addAll(newItems)
        rebuildPositionIndex()
        selectedIds.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun submitList(newItems: List<MediaStoreRepository.MediaItem>, preselectedIds: Set<Long>) {
        items.clear()
        items.addAll(newItems)
        rebuildPositionIndex()
        selectedIds.clear()
        selectedIds.addAll(preselectedIds)
        selectionMode = selectedIds.isNotEmpty()
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun setSelectedIds(ids: Set<Long>) {
        val before = selectedIds.toSet()
        selectedIds.clear()
        selectedIds.addAll(ids)
        selectionMode = selectedIds.isNotEmpty()
        notifySelectionDiff(before, selectedIds)
        onSelectionChanged(selectedIds.size)
    }

    fun getSelectedIdsSnapshot(): Set<Long> = selectedIds.toSet()

    fun idsInPositionRange(from: Int, to: Int): Set<Long> {
        if (items.isEmpty()) return emptySet()
        val start = minOf(from, to).coerceAtLeast(0)
        val end = maxOf(from, to).coerceAtMost(items.size - 1)
        if (start > end) return emptySet()
        val out = LinkedHashSet<Long>((end - start + 1).coerceAtLeast(4))
        for (i in start..end) out.add(items[i].id)
        return out
    }

    fun selectByPosition(position: Int) {
        if (position < 0 || position >= items.size) return
        val id = items[position].id
        val before = selectedIds.toSet()
        if (selectedIds.add(id)) {
            selectionMode = true
            notifySelectionDiff(before, selectedIds)
            onSelectionChanged(selectedIds.size)
        }
    }

    fun getSelectedItems(): List<MediaStoreRepository.MediaItem> {
        if (selectedIds.isEmpty()) return emptyList()
        val selectedSet = selectedIds.toHashSet()
        return items.filter { selectedSet.contains(it.id) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_grid, parent, false)
        return VH(
            view,
            onClick = { item ->
                if (selectionMode) {
                    toggleSelected(item.id)
                } else {
                    onPreviewRequested?.invoke(item)
                }
            },
            onLongClick = { item ->
                selectionMode = true
                toggleSelected(item.id)
            }
        )
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], selectedIds.contains(items[position].id))
    }

    override fun getItemCount(): Int = items.size

    private fun toggleSelected(id: Long) {
        val before = selectedIds.toSet()
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }

        if (selectedIds.isEmpty()) selectionMode = false
        notifySelectionDiff(before, selectedIds)
        onSelectionChanged(selectedIds.size)
    }

    private fun rebuildPositionIndex() {
        idToPosition.clear()
        for (i in items.indices) idToPosition[items[i].id] = i
    }

    private fun notifySelectionDiff(before: Set<Long>, after: Set<Long>) {
        if (items.isEmpty()) return
        val changed = HashSet<Long>((before.size + after.size).coerceAtLeast(8))
        changed.addAll(before)
        changed.addAll(after)
        for (id in changed) {
            val pos = idToPosition[id] ?: continue
            notifyItemChanged(pos)
        }
    }

    internal class VH(
        itemView: View,
        private val onClick: (MediaStoreRepository.MediaItem) -> Unit,
        private val onLongClick: (MediaStoreRepository.MediaItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val image: ImageView = itemView.findViewById(R.id.image)
        private val overlay: View = itemView.findViewById(R.id.selectionOverlay)

        private var current: MediaStoreRepository.MediaItem? = null

        init {
            itemView.setOnClickListener {
                current?.let(onClick)
            }
            itemView.setOnLongClickListener {
                current?.let(onLongClick)
                true
            }
        }

        fun bind(item: MediaStoreRepository.MediaItem, selected: Boolean) {
            current = item
            overlay.visibility = if (selected) View.VISIBLE else View.GONE

            image.load(item.contentUri) {
                crossfade(true)
            }
        }
    }
}
