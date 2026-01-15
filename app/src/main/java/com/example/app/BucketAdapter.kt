package com.example.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

internal class BucketAdapter(
    private val onClick: (MediaStoreRepository.BucketSummary) -> Unit
) : ListAdapter<MediaStoreRepository.BucketSummary, BucketAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bucket, parent, false)
        return VH(view, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    internal class VH(
        itemView: View,
        private val onClick: (MediaStoreRepository.BucketSummary) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val name: TextView = itemView.findViewById(R.id.bucketName)
        private val count: TextView = itemView.findViewById(R.id.bucketCount)
        private val size: TextView = itemView.findViewById(R.id.bucketSize)

        private var current: MediaStoreRepository.BucketSummary? = null

        init {
            itemView.setOnClickListener {
                current?.let(onClick)
            }
        }

        fun bind(item: MediaStoreRepository.BucketSummary) {
            current = item
            name.text = item.name
            count.text = itemView.context.getString(R.string.folder_images_count, item.count)
            size.text = Formatters.bytesToHuman(item.totalBytes)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MediaStoreRepository.BucketSummary>() {
            override fun areItemsTheSame(
                oldItem: MediaStoreRepository.BucketSummary,
                newItem: MediaStoreRepository.BucketSummary
            ): Boolean = oldItem.bucketId == newItem.bucketId

            override fun areContentsTheSame(
                oldItem: MediaStoreRepository.BucketSummary,
                newItem: MediaStoreRepository.BucketSummary
            ): Boolean = oldItem == newItem
        }
    }
}
