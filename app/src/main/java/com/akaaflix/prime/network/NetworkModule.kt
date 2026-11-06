package com.akaaflix.prime.network

import android.util.Log
import com.akaaflix.prime.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Shared JSON configuration tolerant of inconsistent Xtream panels. */
val XtreamJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    allowSpecialFloatingPointValues = true
}

object NetworkModule {
    const val USER_AGENT = "AkaaFlixPrime/${BuildConfig.VERSION_NAME} (Linux; Android) ExoPlayerLib"

    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(UserAgentInterceptor())
        .apply { if (BuildConfig.DEBUG) addInterceptor(RedactingLogInterceptor()) }
        .build()
}

private class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.header("User-Agent") != null) return chain.proceed(req)
        return chain.proceed(req.newBuilder().header("User-Agent", NetworkModule.USER_AGENT).build())
    }
}

/**
 * Debug-only request logger that never prints credentials: query values for
 * username/password and the credential path segments of stream URLs are masked.
 */
class RedactingLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        val response = chain.proceed(request)
        val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        Log.d("AkaaHttp", "${request.method} ${Redactor.redact(request.url.toString())} -> ${response.code} (${ms}ms)")
        return response
    }
}

object Redactor {
    private val queryCreds = Regex("(?i)(username|password|user|pass|token)=([^&#]*)")
    private val streamPath = Regex("(?i)/(live|movie|series|timeshift)/([^/]+)/([^/]+)/")

    fun redact(url: String): String = url
        .replace(queryCreds) { "${it.groupValues[1]}=***" }
        .replace(streamPath) { "/${it.groupValues[1]}/***/***/" }
}

/** Creates (and caches) a Retrofit [XtreamApi] per server base URL. */
class XtreamApiFactory(private val client: OkHttpClient) {
    private val cache = ConcurrentHashMap<String, XtreamApi>()

    fun create(baseUrl: String, fastConnect: Boolean = false): XtreamApi {
        if (fastConnect) return build(baseUrl, client.newBuilder().connectTimeout(7, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build())
        return cache.getOrPut(baseUrl) { build(baseUrl, client) }
    }

    private fun build(baseUrl: String, okHttp: OkHttpClient): XtreamApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .build()
        .create(XtreamApi::class.java)
}
