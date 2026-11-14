package com.akaaflix.launcher.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PagingMathTest {
    @Test fun pageCounts() {
        assertEquals(1, PagingMath.pageCount(0, 28))
        assertEquals(1, PagingMath.pageCount(28, 28))
        assertEquals(2, PagingMath.pageCount(29, 28))
        assertEquals(4, PagingMath.pageCount(100, 28))
    }

    @Test fun unpagedGridDoesNotOverflow() {
        assertEquals(1, PagingMath.pageCount(23, Int.MAX_VALUE))
        assertEquals(0 until 23, PagingMath.pageRange(23, 0, Int.MAX_VALUE))
        assertEquals(0, PagingMath.clampPage(5, 23, Int.MAX_VALUE))
    }

    @Test fun ranges() {
        assertEquals(28 until 56, PagingMath.pageRange(100, 1, 28))
        assertEquals(84 until 100, PagingMath.pageRange(100, 3, 28))
        assertEquals(3, PagingMath.clampPage(9, 100, 28))
    }

    @Test fun targetKeepsColumnOrClamps() {
        assertEquals(3, PagingMath.targetPosition(28, 7, 3, 0))
        assertEquals(15, PagingMath.targetPosition(16, 7, 6, 3)) // last page shorter
        assertEquals(10, PagingMath.fit(1200, 110, 3, 10))
    }
}
