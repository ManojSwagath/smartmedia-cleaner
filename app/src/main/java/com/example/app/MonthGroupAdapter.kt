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

internal class MonthGroupAdapter(
    private val onClick: (MonthGroup) -> Unit
) : RecyclerView.Adapter<MonthGroupAdapter.VH>() {

    private val items = mutableListOf<MonthGroup>()

    fun submitList(newItems: List<MonthGroup>) {
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

    internal class VH(itemView: View, private val onClick: (MonthGroup) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val preview: ImageView = itemView.findViewById(R.id.preview)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)

        private var current: MonthGroup? = null

        init {
            itemView.setOnClickListener { current?.let(onClick) }
        }

        fun bind(group: MonthGroup) {
            current = group
            title.text = group.title
            subtitle.text = "${group.count} items • ${Formatters.bytesToHuman(group.totalBytes)}"

            val uri = group.previewId?.let {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it)
            }
            preview.load(uri) { crossfade(true) }
        }
    }
}
