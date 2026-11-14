package com.akaaflix.launcher.device.update

import com.akaaflix.launcher.core.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateResult {
    data class Available(val version: String, val pageUrl: String, val apkUrl: String?, val notes: String) : UpdateResult()
    data object UpToDate : UpdateResult()
    data object NoRelease : UpdateResult()
    data object NotConfigured : UpdateResult()
    data class Failed(val reason: String) : UpdateResult()
}

/**
 * Checks GitHub releases of the configured repository for a tag "<prefix><version>" (e.g.
 * "launcher-v1.0.1"). Runs only when the user asks (or weekly when enabled). No data is sent.
 */
object UpdateChecker {
    suspend fun check(repo: String = AppConfig.updateRepo, prefix: String = AppConfig.updateTagPrefix,
                      current: String = AppConfig.versionName): UpdateResult = withContext(Dispatchers.IO) {
        if (repo.isBlank() || !repo.contains('/')) return@withContext UpdateResult.NotConfigured
        try {
            val c = URL("https://api.github.com/repos/$repo/releases?per_page=30").openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "AkaaFlixLauncher/${AppConfig.versionName}")
            val body = try {
                if (c.responseCode == 404) return@withContext UpdateResult.NoRelease
                if (c.responseCode !in 200..299) return@withContext UpdateResult.Failed("HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            } finally { c.disconnect() }
            parse(body, prefix, current)
        } catch (e: Exception) {
            UpdateResult.Failed(e.javaClass.simpleName)
        }
    }

    fun parse(json: String, prefix: String, current: String): UpdateResult {
        val arr = JSONArray(json)
        var best: UpdateResult.Available? = null
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val tag = r.optString("tag_name")
            if (!tag.startsWith(prefix)) continue
            val version = tag.removePrefix(prefix).removePrefix("v")
            if (best != null && compareVersions(version, best.version) <= 0) continue
            val assets = r.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (j in 0 until assets.length()) {
                val a = assets.optJSONObject(j) ?: continue
                if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
            }
            best = UpdateResult.Available(version, r.optString("html_url"), apk, r.optString("body"))
        }
        val b = best ?: return UpdateResult.NoRelease
        return if (compareVersions(b.version, current.substringBefore('-')) > 0) b else UpdateResult.UpToDate
    }

    /** Numeric dotted comparison: "1.10.0" > "1.9.3". Non-numeric parts count as 0. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
