package com.example.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

internal data class FeatureTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val backgroundColorRes: Int
)

internal class FeatureTileAdapter(
    private val onClick: (FeatureTile) -> Unit
) : ListAdapter<FeatureTile, FeatureTileAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feature_tile, parent, false)
        return VH(view, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    internal class VH(itemView: View, private val onClick: (FeatureTile) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val iconImage: ImageView = itemView.findViewById(R.id.iconImage)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)

        private var current: FeatureTile? = null

        init {
            val pressScale = 0.97f
            val settleInterpolator = OvershootInterpolator(1.2f)

            itemView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().cancel()
                        v.animate()
                            .scaleX(pressScale)
                            .scaleY(pressScale)
                            .setDuration(90)
                            .start()
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().cancel()
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(160)
                            .setInterpolator(settleInterpolator)
                            .start()
                    }
                }
                false
            }

            itemView.setOnClickListener {
                itemView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                current?.let(onClick)
            }
        }

        fun bind(tile: FeatureTile) {
            current = tile
            title.text = tile.title
            subtitle.text = tile.subtitle
            iconImage.setImageResource(tile.iconRes)
            iconImage.contentDescription = tile.title

            val color = ContextCompat.getColor(itemView.context, tile.backgroundColorRes)
            (itemView as? MaterialCardView)?.setCardBackgroundColor(color)
                ?: itemView.setBackgroundColor(color)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FeatureTile>() {
            override fun areItemsTheSame(oldItem: FeatureTile, newItem: FeatureTile): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: FeatureTile, newItem: FeatureTile): Boolean = oldItem == newItem
        }
    }
}
