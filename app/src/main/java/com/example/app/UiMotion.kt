package com.example.app

import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

internal fun animatePopOffSelected(
    recyclerView: RecyclerView,
    selectedIds: Set<Long>,
    idAtPosition: (Int) -> Long?
) {
    if (selectedIds.isEmpty()) return

    val density = recyclerView.resources.displayMetrics.density
    val dy = -10f * density

    for (i in 0 until recyclerView.childCount) {
        val child = recyclerView.getChildAt(i) ?: continue
        val holder = recyclerView.getChildViewHolder(child)
        val pos = holder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) continue

        val id = idAtPosition(pos) ?: continue
        if (!selectedIds.contains(id)) continue

        child.animate().cancel()
        child.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .translationY(dy)
            .setDuration(180)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }
}
