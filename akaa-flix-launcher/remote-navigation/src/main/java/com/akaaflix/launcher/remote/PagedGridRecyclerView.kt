package com.akaaflix.launcher.remote

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView for D-pad grids:
 *  - the focused card is drawn last, so its zoom/glow is never hidden by neighbours;
 *  - reaching an edge can turn the page (DOWN on the last row, UP on the first row,
 *    RIGHT on the last column) through [edgeListener];
 *  - focus never escapes sideways into an unrelated region by accident.
 */
class PagedGridRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RecyclerView(context, attrs) {

    enum class Edge { TOP, BOTTOM, END }

    fun interface EdgeListener {
        /** Return true when the edge was handled (e.g. the page changed) and focus must stay. */
        fun onEdge(edge: Edge, column: Int, row: Int): Boolean
    }

    var columns: Int = 1
    var edgeListener: EdgeListener? = null

    init {
        isChildrenDrawingOrderEnabled = true
        clipChildren = false
        clipToPadding = false
        itemAnimator = null
        overScrollMode = OVER_SCROLL_NEVER
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        val focused = focusedChild ?: return drawingPosition
        val f = indexOfChild(focused)
        if (f < 0) return drawingPosition
        return when {
            drawingPosition == childCount - 1 -> f
            drawingPosition >= f -> drawingPosition + 1
            else -> drawingPosition
        }
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val listener = edgeListener
        val holder = findContainingViewHolder(focused)
        val pos = holder?.bindingAdapterPosition ?: NO_POSITION
        val count = adapter?.itemCount ?: 0
        if (listener != null && pos != NO_POSITION && count > 0 && columns > 0) {
            val col = pos % columns
            val row = pos / columns
            val lastRow = (count - 1) / columns
            val handled = when (direction) {
                View.FOCUS_DOWN -> row == lastRow && listener.onEdge(Edge.BOTTOM, col, row)
                View.FOCUS_UP -> row == 0 && listener.onEdge(Edge.TOP, col, row)
                View.FOCUS_RIGHT -> (col == columns - 1 || pos == count - 1) && listener.onEdge(Edge.END, col, row)
                else -> false
            }
            if (handled) return focused
        }
        return super.focusSearch(focused, direction)
    }

    /** Requests focus on [position] once it is laid out. */
    fun focusPosition(position: Int) {
        val vh = findViewHolderForAdapterPosition(position)
        if (vh != null && !isLayoutRequested) { vh.itemView.requestFocus(); return }
        post {
            (findViewHolderForAdapterPosition(position)?.itemView ?: getChildAt(0))?.requestFocus()
        }
    }
}
