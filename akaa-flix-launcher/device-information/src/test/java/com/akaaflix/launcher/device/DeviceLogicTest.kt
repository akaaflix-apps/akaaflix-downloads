package com.akaaflix.launcher.device

import com.akaaflix.launcher.device.status.NetworkMonitor
import com.akaaflix.launcher.device.update.UpdateChecker
import com.akaaflix.launcher.device.update.UpdateResult
import com.akaaflix.launcher.device.weather.WeatherKind
import com.akaaflix.launcher.device.weather.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceLogicTest {
    @Test fun versionComparison() {
        assertTrue(UpdateChecker.compareVersions("1.10.0", "1.9.3") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0", "1.0.0") == 0)
        assertTrue(UpdateChecker.compareVersions("2.0.0", "10.0.0") < 0)
    }

    @Test fun parsesGithubReleasesByPrefix() {
        val json = """[
          {"tag_name":"prime-v9.0.0","draft":false,"prerelease":false,"html_url":"p","assets":[]},
          {"tag_name":"launcher-v1.2.0","draft":false,"prerelease":false,"html_url":"https://x/1.2.0","body":"notes","assets":[{"name":"AkaaFlixLauncher.apk","browser_download_url":"https://x/a.apk"}]},
          {"tag_name":"launcher-v1.3.0","draft":true,"prerelease":false,"html_url":"d","assets":[]},
          {"tag_name":"launcher-v1.1.0","draft":false,"prerelease":false,"html_url":"o","assets":[]}
        ]"""
        val r = UpdateChecker.parse(json, "launcher-v", "1.0.0")
        assertTrue(r is UpdateResult.Available)
        r as UpdateResult.Available
        assertEquals("1.2.0", r.version)
        assertEquals("https://x/a.apk", r.apkUrl)
        assertEquals(UpdateResult.UpToDate, UpdateChecker.parse(json, "launcher-v", "1.2.0"))
        assertEquals(UpdateResult.NoRelease, UpdateChecker.parse("[]", "launcher-v", "1.0.0"))
    }

    @Test fun wifiLevels() {
        assertEquals(3, NetworkMonitor.levelFromRssi(-50))
        assertEquals(2, NetworkMonitor.levelFromRssi(-60))
        assertEquals(1, NetworkMonitor.levelFromRssi(-75))
        assertEquals(0, NetworkMonitor.levelFromRssi(-90))
    }

    @Test fun weatherCodes() {
        assertEquals(WeatherKind.CLEAR, WeatherRepository.kind(0))
        assertEquals(WeatherKind.PARTLY_CLOUDY, WeatherRepository.kind(2))
        assertEquals(WeatherKind.RAIN, WeatherRepository.kind(61))
        assertEquals(WeatherKind.SNOW, WeatherRepository.kind(73))
        assertEquals(WeatherKind.THUNDER, WeatherRepository.kind(95))
        assertEquals(71.6, WeatherRepository.toFahrenheit(22.0), 0.01)
    }
}
