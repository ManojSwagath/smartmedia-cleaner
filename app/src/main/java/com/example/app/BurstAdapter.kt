package com.example.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

internal class BurstAdapter(
    private val onClick: (BurstCluster) -> Unit
) : RecyclerView.Adapter<BurstAdapter.VH>() {

    private val items = mutableListOf<BurstCluster>()

    fun submitList(newItems: List<BurstCluster>) {
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

    internal class VH(itemView: View, private val onClick: (BurstCluster) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val preview: ImageView = itemView.findViewById(R.id.preview)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)

        private var current: BurstCluster? = null

        init {
            itemView.setOnClickListener { current?.let(onClick) }
        }

        fun bind(cluster: BurstCluster) {
            current = cluster
            val deleteCount = cluster.suggestedDeleteIds.size
            title.text = "Burst (${cluster.items.size})"
            subtitle.text = "Keep 1, delete $deleteCount"
            preview.load(cluster.keep.contentUri) { crossfade(true) }
        }
    }
}
