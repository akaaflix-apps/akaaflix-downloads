package com.akaaflix.launcher.remote

/** Pure helpers for the paged app / wallpaper grids (unit tested). */
object PagingMath {
    fun pageCount(items: Int, pageSize: Int): Int =
        if (items <= 0 || pageSize <= 0) 1 else ((items.toLong() + pageSize - 1) / pageSize).toInt().coerceAtLeast(1)

    fun pageRange(items: Int, page: Int, pageSize: Int): IntRange {
        if (items <= 0 || pageSize <= 0) return IntRange.EMPTY
        val start = (page.toLong() * pageSize).coerceIn(0L, items.toLong()).toInt()
        val end = (start.toLong() + pageSize).coerceAtMost(items.toLong()).toInt()
        return start until end
    }

    fun clampPage(page: Int, items: Int, pageSize: Int): Int = page.coerceIn(0, pageCount(items, pageSize) - 1)

    /** Number of cells that fit into [availablePx] when each needs at least [minCellPx]. */
    fun fit(availablePx: Int, minCellPx: Int, minCount: Int, maxCount: Int): Int {
        if (minCellPx <= 0) return minCount
        return (availablePx / minCellPx).coerceIn(minCount, maxCount)
    }

    /** Position to focus after moving to another page, keeping the column (or row) when possible. */
    fun targetPosition(itemsOnPage: Int, columns: Int, column: Int, row: Int): Int {
        if (itemsOnPage <= 0) return 0
        val p = row * columns + column
        return p.coerceIn(0, itemsOnPage - 1)
    }
}
