package com.akaaflix.launcher.device

import com.akaaflix.launcher.device.status.NetworkMonitor
import com.akaaflix.launcher.device.update.UpdateChecker
import com.akaaflix.launcher.device.update.UpdateResult
import com.akaaflix.launcher.device.weather.Place
import com.akaaflix.launcher.device.weather.WeatherKind
import com.akaaflix.launcher.device.weather.WeatherParsers
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
        assertEquals(WeatherKind.DRIZZLE, WeatherRepository.kind(53))
        assertEquals(71.6, WeatherRepository.toFahrenheit(22.0), 0.01)
    }

    /** Documented Open-Meteo forecast response (current + daily). */
    @Test fun parsesForecastWithHighLow() {
        val json = """{"latitude":30.04,"longitude":31.24,"timezone":"Africa/Cairo",
            "current":{"time":"2026-11-14T00:45","interval":900,"temperature_2m":22.3,"weather_code":2,"is_day":0},
            "daily":{"time":["2026-11-14"],"temperature_2m_max":[27.1],"temperature_2m_min":[16.4]}}"""
        val w = WeatherParsers.forecast(json, "Cairo", "Egypt", 42L)
        assertEquals(22.3, w.temperatureC, 0.001)
        assertEquals(2, w.weatherCode)
        assertEquals(false, w.isDay)
        assertEquals(27.1, w.highC, 0.001)
        assertEquals(16.4, w.lowC, 0.001)
        assertTrue(w.hasHighLow)
        assertEquals("Cairo", w.city)
        val noDaily = WeatherParsers.forecast("""{"current":{"temperature_2m":5,"weather_code":71}}""", "Oslo", "", 1L)
        assertEquals(false, noDaily.hasHighLow)
        assertEquals(true, noDaily.isDay)
    }

    @Test fun parsesCitySearchAndReverseGeocoding() {
        val search = """{"results":[{"id":360630,"name":"Cairo","latitude":30.06263,"longitude":31.24967,"country":"Egypt","admin1":"Cairo Governorate"},
            {"id":1,"name":"","latitude":1,"longitude":2},{"id":2,"name":"NoCoords"}]}"""
        val r = WeatherParsers.search(search)
        assertEquals(1, r.size)
        assertEquals("Cairo", r[0].name)
        assertEquals("Cairo Governorate", r[0].admin1)
        assertTrue(WeatherParsers.search("""{"generationtime_ms":0.5}""").isEmpty())
        val bdc = """{"latitude":30.04,"longitude":31.24,"countryName":"مصر","countryCode":"EG","principalSubdivision":"محافظة القاهرة","city":"القاهرة","locality":"وسط البلد"}"""
        assertEquals(Place("القاهرة", "مصر"), WeatherParsers.bigDataCloud(bdc))
        assertEquals(Place("Village", "France"), WeatherParsers.bigDataCloud("""{"city":"","locality":"Village","countryName":"France"}"""))
        assertEquals(null, WeatherParsers.bigDataCloud("""{"city":"","locality":"","principalSubdivision":""}"""))
    }

    @Test fun coordinatesAreRoundedBeforeUse() {
        assertEquals(30.04, WeatherParsers.round2(30.04456), 1e-9)
        assertEquals(-0.13, WeatherParsers.round2(-0.1275), 1e-9)
    }
}
