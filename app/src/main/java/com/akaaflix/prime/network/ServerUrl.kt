package com.akaaflix.prime.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Normalises the many server URL formats users paste from their provider, e.g.
 * `host:8080`, `http://host:8080/`, `https://host/player_api.php?username=..`,
 * `http://1.2.3.4:25461/get.php?...`, `http://host/c/` or `[::1]:80`.
 */
object ServerUrl {

    data class Normalized(
        /** Base URL ending with "/", e.g. `http://host:8080/`. */
        val baseUrl: String,
        /** True when the user did not type a scheme (we may probe HTTPS then HTTP). */
        val schemeWasGuessed: Boolean,
    ) {
        val host: String get() = baseUrl.toHttpUrlOrNull()?.host.orEmpty()
    }

    fun normalize(raw: String, defaultScheme: String = "http"): Normalized? {
        var input = raw.trim().replace("\\", "/")
        if (input.isEmpty() || input.any { it.isWhitespace() }) return null
        val hasScheme = input.contains("://")
        if (hasScheme) {
            val scheme = input.substringBefore("://").lowercase()
            if (scheme != "http" && scheme != "https") return null
        } else {
            input = "$defaultScheme://$input"
        }
        val parsed: HttpUrl = input.toHttpUrlOrNull() ?: return null
        if (parsed.host.isBlank()) return null
        if (!isPlausibleHost(parsed.host)) return null

        // Drop query/fragment and any trailing well-known endpoint.
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        while (segments.isNotEmpty() && segments.last().endsWith(".php", ignoreCase = true)) {
            segments.removeAt(segments.lastIndex)
        }
        val builder = parsed.newBuilder().query(null).fragment(null).encodedPath("/")
        segments.forEach { builder.addPathSegment(it) }
        val base = builder.build().toString().let { if (it.endsWith("/")) it else "$it/" }
        return Normalized(baseUrl = base, schemeWasGuessed = !hasScheme)
    }

    /** Same server reachable with the other scheme (used when the scheme was guessed). */
    fun withScheme(baseUrl: String, scheme: String): String? =
        baseUrl.toHttpUrlOrNull()?.newBuilder()?.scheme(scheme)?.build()?.toString()

    private fun isPlausibleHost(host: String): Boolean {
        if (host.contains(':')) return true // IPv6
        if (host == "localhost") return true
        if (host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) {
            return host.split('.').all { it.toInt() in 0..255 }
        }
        return host.contains('.') && host.split('.').all { it.isNotEmpty() && it.length <= 63 }
    }

    /** Host only (no credentials, path or query) — safe to show in UI and logs. */
    fun displayHost(baseUrl: String): String {
        val url = baseUrl.toHttpUrlOrNull() ?: return ""
        val port = if (url.port != HttpUrl.defaultPort(url.scheme)) ":${url.port}" else ""
        return "${url.scheme}://${url.host}$port"
    }
}
