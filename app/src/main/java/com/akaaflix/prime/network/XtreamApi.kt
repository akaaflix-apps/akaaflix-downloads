package com.akaaflix.prime.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Streaming

/**
 * Standard Xtream Codes player API. All calls go through `player_api.php` with the
 * playlist credentials; bodies are streamed so very large catalogs are decoded
 * incrementally instead of being buffered as a single string.
 */
interface XtreamApi {
    @Streaming
    @GET("player_api.php")
    suspend fun playerApi(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String? = null,
        @QueryMap extra: Map<String, String> = emptyMap(),
    ): ResponseBody
}

object XtreamActions {
    const val LIVE_CATEGORIES = "get_live_categories"
    const val VOD_CATEGORIES = "get_vod_categories"
    const val SERIES_CATEGORIES = "get_series_categories"
    const val LIVE_STREAMS = "get_live_streams"
    const val VOD_STREAMS = "get_vod_streams"
    const val SERIES = "get_series"
    const val VOD_INFO = "get_vod_info"
    const val SERIES_INFO = "get_series_info"
    const val SHORT_EPG = "get_short_epg"
}
