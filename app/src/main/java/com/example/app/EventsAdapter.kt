package com.example.app

import android.content.ContentUris
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

internal class EventsAdapter(
    private val onClick: (EventGroup) -> Unit
) : RecyclerView.Adapter<EventsAdapter.VH>() {

    private val items = mutableListOf<EventGroup>()

    fun submitList(newItems: List<EventGroup>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cluster, parent, false)
        return VH(view, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    internal class VH(itemView: View, private val onClick: (EventGroup) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val preview: ImageView = itemView.findViewById(R.id.preview)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)

        private var current: EventGroup? = null

        init {
            itemView.setOnClickListener { current?.let(onClick) }
        }

        fun bind(event: EventGroup) {
            current = event
            title.text = event.title
            subtitle.text = "${event.count} items • ${Formatters.bytesToHuman(event.totalBytes)}"
            val firstId = event.ids.firstOrNull()
            val uri = if (firstId != null) {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, firstId)
            } else {
                null
            }
            preview.load(uri) { crossfade(true) }
        }
    }
}
