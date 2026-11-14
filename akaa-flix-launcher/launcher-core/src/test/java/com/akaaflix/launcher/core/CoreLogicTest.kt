package com.akaaflix.launcher.core

import com.akaaflix.launcher.core.perf.DeviceProfile
import com.akaaflix.launcher.core.perf.DevicePerformance
import com.akaaflix.launcher.core.perf.PerformanceTier
import com.akaaflix.launcher.core.util.Bytes
import com.akaaflix.launcher.core.util.Numerals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    private val gb = 1024L * 1024 * 1024

    @Test fun tiersCoverOneToEightGb() {
        assertEquals(PerformanceTier.LOW, DevicePerformance.tierFor(1 * gb, false, 4))
        assertEquals(PerformanceTier.MEDIUM, DevicePerformance.tierFor(2 * gb, false, 4))
        assertEquals(PerformanceTier.HIGH, DevicePerformance.tierFor(4 * gb, false, 8))
        assertEquals(PerformanceTier.ULTRA, DevicePerformance.tierFor(8 * gb, false, 8))
        assertEquals(PerformanceTier.LOW, DevicePerformance.tierFor(4 * gb, true, 8))   // isLowRamDevice wins
    }

    @Test fun lightweightModeDisablesHeavyEffects() {
        val low = DeviceProfile(1 * gb, 96, 4, false, true, false, PerformanceTier.LOW)
        val q = DevicePerformance.quality("auto", low)
        assertEquals("light", q.mode)
        assertFalse(q.videoWallpapers); assertFalse(q.glow); assertFalse(q.motion); assertTrue(q.wallpaperRgb565)
        val high = DevicePerformance.quality("auto", low.copy(totalRamBytes = 8 * gb, tier = PerformanceTier.ULTRA))
        assertEquals("high", high.mode)
        assertTrue(high.videoWallpapers && high.glow && high.motion)
        assertEquals("light", DevicePerformance.quality("light", low.copy(tier = PerformanceTier.ULTRA)).mode) // user override
    }

    @Test fun numeralsAndBytes() {
        assertEquals("١٢:٤٥", Numerals.localize("12:45", true))
        assertEquals("12:45", Numerals.localize("12:45", false))
        assertEquals("568 GB", Bytes.storage(568_000_000_000))
        assertEquals("1 TB", Bytes.storage(1_000_000_000_000))
        assertEquals("6.2 GB", Bytes.memory((6.2 * gb).toLong()))
    }
}
