package com.akaaflix.prime.testutil

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * MOCK DEVELOPMENT SERVER (tests only). Emulates an Xtream Codes `player_api.php` endpoint,
 * including the inconsistent field types real panels return. It never streams video.
 */
class FakeXtreamServer(
    private val username: String = "demo_user",
    private val password: String = "s3cret!",
) {

    var status = "Active"
    var expDate: String? = "1893456000" // 2030-01-01
    var liveStreams = LIVE_STREAMS
    var vodStreams = VOD_STREAMS
    var seriesList = SERIES
    var seriesInfo = SERIES_INFO
    /** When set, this action answers HTTP 500. */
    var failAction: String? = null
    val requests = mutableListOf<RecordedRequest>()

    val server = MockWebServer().also { mws ->
        mws.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
        }
    }

    fun start(): String {
        server.start()
        return server.url("/").toString()
    }

    fun shutdown() = server.shutdown()

    private fun handle(request: RecordedRequest): MockResponse {
        requests += request
        val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
        if (url.encodedPath != "/player_api.php") return MockResponse().setResponseCode(404)
        if (url.queryParameter("username") != username || url.queryParameter("password") != password) {
            return json("""{"user_info":{"auth":0}}""")
        }
        val action = url.queryParameter("action")
        if (action != null && action == failAction) return MockResponse().setResponseCode(500)
        return when (action) {
            null -> json(auth())
            "get_live_categories" -> json(LIVE_CATEGORIES)
            "get_vod_categories" -> json(VOD_CATEGORIES)
            "get_series_categories" -> json(SERIES_CATEGORIES)
            "get_live_streams" -> json(liveStreams)
            "get_vod_streams" -> json(vodStreams)
            "get_series" -> json(seriesList)
            "get_vod_info" -> json(VOD_INFO)
            "get_series_info" -> json(seriesInfo)
            "get_short_epg" -> json(shortEpg())
            else -> json("[]")
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun auth() = """
        {"user_info":{"username":"$username","password":"$password","message":"Welcome to the demo panel","auth":1,
        "status":"$status","exp_date":${expDate?.let { "\"$it\"" } ?: "null"},"is_trial":"0","active_cons":"1",
        "created_at":"1700000000","max_connections":"2","allowed_output_formats":["m3u8","ts"]},
        "server_info":{"url":"demo.local","port":"8080","https_port":"8443","server_protocol":"http","rtmp_port":"0",
        "timezone":"Africa/Cairo","timestamp_now":1700000000,"time_now":"2023-11-14 22:13:20"}}
    """.trimIndent()

    private fun shortEpg(): String {
        val now = System.currentTimeMillis() / 1000
        // "Morning News" / "Evening Talk" in base64, as Xtream panels send them.
        return """{"epg_listings":[
            {"title":"TW9ybmluZyBOZXdz","description":"RGFpbHkgaGVhZGxpbmVz","start_timestamp":"${now - 600}","stop_timestamp":"${now + 1200}"},
            {"title":"RXZlbmluZyBUYWxr","description":"","start_timestamp":"${now + 1200}","stop_timestamp":"${now + 4800}"}
        ]}"""
    }

    companion object {
        const val LIVE_CATEGORIES = """[{"category_id":"1","category_name":"AR | News","parent_id":0},
            {"category_id":2,"category_name":"Kids Cartoons","parent_id":"0"},
            {"category_id":"3","category_name":"XXX Adults 18+","parent_id":0}]"""
        const val LIVE_STREAMS = """[
            {"num":1,"name":"Al News HD","stream_type":"live","stream_id":101,"stream_icon":"http://img.local/1.png","epg_channel_id":"news.ar","added":"1700000000","is_adult":"0","category_id":"1","tv_archive":0},
            {"num":"2","name":"Cartoon Fun","stream_id":"102","stream_icon":"","added":1700000100,"category_id":"2","is_adult":0},
            {"num":3,"name":"Night Show","stream_id":103,"category_id":"3","is_adult":"1"}]"""
        const val VOD_CATEGORIES = """[{"category_id":"10","category_name":"Action"},{"category_id":"11","category_name":"Drama"}]"""
        const val VOD_STREAMS = """[
            {"num":1,"name":"Desert Planet (2024) 4K","stream_id":201,"stream_icon":"http://img.local/m1.jpg","rating":"8.1","rating_5based":4.05,"added":"1700001000","category_id":"10","container_extension":"mkv"},
            {"num":2,"name":"Quiet Harbor","stream_id":"202","stream_icon":null,"rating":"","added":"1700000500","category_id":"11","container_extension":"mp4","year":"2021"},
            {"num":3,"name":"Old Classic","stream_id":203,"rating":6,"added":"1600000000","category_id":"11","container_extension":"avi"}]"""
        const val SERIES_CATEGORIES = """[{"category_id":"20","category_name":"Drama Series"}]"""
        const val SERIES = """[
            {"num":1,"name":"Kingdom Tales","series_id":301,"cover":"http://img.local/s1.jpg","plot":"A story","genre":"Drama","releaseDate":"2023-05-01","last_modified":"1700002000","rating":"8.5","backdrop_path":["http://img.local/b1.jpg"],"category_id":"20"},
            {"num":2,"name":"City Lights","series_id":"302","cover":"","backdrop_path":"","category_id":"20","last_modified":1690000000}]"""
        const val VOD_INFO = """{"info":{"plot":"A long journey across the dunes.","genre":"Sci-Fi","duration_secs":"9000","backdrop_path":["http://img.local/bd.jpg"],"age":"16+","cast":"Actor One, Actor Two","director":"Director X","releasedate":"2024-03-01"},
            "movie_data":{"stream_id":201,"name":"Desert Planet (2024) 4K","container_extension":"mkv","category_id":"10"}}"""
        const val SERIES_INFO = """{"seasons":[{"season_number":1,"name":"Season 1","episode_count":2},{"season_number":5,"name":"Empty season"}],
            "info":{"name":"Kingdom Tales","cast":"A, B","director":"C","backdrop_path":["http://img.local/b1.jpg"]},
            "episodes":{"1":[{"id":"9001","episode_num":1,"title":"Pilot","container_extension":"mkv","info":{"duration_secs":2700,"movie_image":"http://img.local/e1.jpg"},"season":1},
                             {"id":9002,"episode_num":"2","title":"Second","container_extension":"mp4","info":[],"season":"1"}],
                        "2":[{"id":"9003","episode_num":1,"title":"S2E1","container_extension":"mkv","season":2}]}}"""
        const val SERIES_INFO_ARRAY_EPISODES = """{"seasons":[],"info":[],
            "episodes":[[{"id":"7001","episode_num":2,"title":"B","season":1},{"id":"7000","episode_num":1,"title":"A","season":1}]]}"""
    }
}
