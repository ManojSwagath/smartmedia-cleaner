package com.example.app

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Enables “long-press then drag to select multiple” behavior on a media grid.
 *
 * This intentionally only activates while the adapter is already in selection mode
 * (entered via long-press on an item). While dragging, any item under the finger
 * becomes selected (no toggling off).
 */
internal class DragSelectTouchListener(
    private val adapter: MediaAdapter
) : RecyclerView.OnItemTouchListener {

    private var pointerDown = false
    private var dragSelecting = false

    private var detector: GestureDetectorCompat? = null

    private var downX = 0f
    private var downY = 0f

    private var lastSelectedPosition: Int = RecyclerView.NO_POSITION

    private var dragAnchorPosition: Int = RecyclerView.NO_POSITION
    private var dragBaseSelectedIds: Set<Long> = emptySet()

    private var activeRv: RecyclerView? = null

    private fun ensureDetector(rv: RecyclerView) {
        if (detector != null && activeRv === rv) return

        activeRv = rv
        detector = GestureDetectorCompat(
            rv.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    // Only start drag-select when the user truly long-presses on an item.
                    val recycler = activeRv ?: return
                    val child = recycler.findChildViewUnder(e.x, e.y) ?: return
                    val position = recycler.getChildAdapterPosition(child)
                    if (position == RecyclerView.NO_POSITION) return

                    adapter.selectByPosition(position)
                    dragBaseSelectedIds = adapter.getSelectedIdsSnapshot()
                    dragAnchorPosition = position
                    dragSelecting = true
                    lastSelectedPosition = position
                    recycler.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        )
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        ensureDetector(rv)
        detector?.onTouchEvent(e)

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerDown = true
                dragSelecting = false
                downX = e.x
                downY = e.y
                lastSelectedPosition = RecyclerView.NO_POSITION
                dragAnchorPosition = RecyclerView.NO_POSITION
                dragBaseSelectedIds = emptySet()
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!pointerDown) return false

                // If drag-select is active, keep selecting under finger.
                if (dragSelecting) {
                    selectUnder(rv, e)
                    return true
                }

                // Otherwise, allow normal scroll.
                return false
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                reset()
                return false
            }
        }

        return dragSelecting
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (!dragSelecting) return

        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> selectUnder(rv, e)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> reset()
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit

    private fun selectUnder(rv: RecyclerView, e: MotionEvent) {
        val child = rv.findChildViewUnder(e.x, e.y) ?: return
        val position = rv.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION) return
        if (position == lastSelectedPosition) return

        val anchor = if (dragAnchorPosition != RecyclerView.NO_POSITION) dragAnchorPosition else position
        val rangeIds = adapter.idsInPositionRange(anchor, position)
        val merged = LinkedHashSet<Long>(dragBaseSelectedIds.size + rangeIds.size)
        merged.addAll(dragBaseSelectedIds)
        merged.addAll(rangeIds)
        adapter.setSelectedIds(merged)

        lastSelectedPosition = position
    }

    private fun reset() {
        pointerDown = false
        dragSelecting = false
        lastSelectedPosition = RecyclerView.NO_POSITION
        dragAnchorPosition = RecyclerView.NO_POSITION
        dragBaseSelectedIds = emptySet()
    }
}
