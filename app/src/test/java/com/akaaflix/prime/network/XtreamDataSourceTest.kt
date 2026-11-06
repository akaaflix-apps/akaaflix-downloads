package com.akaaflix.prime.network

import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.domain.model.AccountStatus
import com.akaaflix.prime.domain.model.XtreamCredentials
import com.akaaflix.prime.testutil.FakeXtreamServer
import com.akaaflix.prime.testutil.testClient
import com.akaaflix.prime.testutil.xtream
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XtreamDataSourceTest {
    private lateinit var fake: FakeXtreamServer
    private lateinit var base: String

    @Before fun setUp() { fake = FakeXtreamServer(); base = fake.start() }
    @After fun tearDown() = fake.shutdown()

    private fun creds(user: String = "demo_user", pass: String = "s3cret!") = XtreamCredentials(base, user, pass)

    @Test fun authenticate_success_parsesStringNumbersAndFormats() = runBlocking {
        val r = xtream().authenticate(creds())
        assertTrue(r is AppResult.Success)
        val info = (r as AppResult.Success).data
        assertEquals(AccountStatus.ACTIVE, info.status)
        assertEquals(1893456000L, info.expiresAtEpochSec)
        assertEquals(1700000000L, info.createdAtEpochSec)
        assertEquals(2, info.maxConnections)
        assertEquals(1, info.activeConnections)
        assertEquals(listOf("m3u8", "ts"), info.allowedOutputFormats)
        assertEquals("Africa/Cairo", info.serverTimezone)
        assertFalse(info.isTrial)
        // Credentials are sent to player_api.php as query parameters.
        val req = fake.requests.single().requestUrl!!
        assertEquals("/player_api.php", req.encodedPath)
        assertEquals("demo_user", req.queryParameter("username"))
        assertNull(req.queryParameter("action"))
    }

    @Test fun authenticate_nullExpiry_meansUnlimited() = runBlocking {
        fake.expDate = null
        val info = (xtream().authenticate(creds()) as AppResult.Success).data
        assertNull(info.expiresAtEpochSec)
    }

    @Test fun authenticate_wrongPassword_isInvalidCredentials() = runBlocking {
        val r = xtream().authenticate(creds(pass = "nope"))
        assertEquals(AppError.InvalidCredentials, (r as AppResult.Failure).error)
    }

    @Test fun authenticate_expiredAccount_isReported() = runBlocking {
        fake.status = "Expired"
        assertEquals(AppError.AccountExpired, (xtream().authenticate(creds()) as AppResult.Failure).error)
        fake.status = "Banned"
        assertEquals(AppError.AccountBanned, (xtream().authenticate(creds()) as AppResult.Failure).error)
        fake.status = "Disabled"
        assertEquals(AppError.AccountDisabled, (xtream().authenticate(creds()) as AppResult.Failure).error)
    }

    @Test fun httpErrors_areMapped() = runBlocking {
        val server = MockWebServer()
        server.start()
        val c = XtreamCredentials(server.url("/").toString(), "u", "p")
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(AppError.InvalidCredentials, (xtream().authenticate(c) as AppResult.Failure).error)
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(AppError.NotXtreamServer, (xtream().authenticate(c) as AppResult.Failure).error)
        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(AppError.Server(502), (xtream().authenticate(c) as AppResult.Failure).error)
        server.enqueue(MockResponse().setBody("<html>not json</html>"))
        assertEquals(AppError.InvalidResponse, (xtream().authenticate(c) as AppResult.Failure).error)
        server.enqueue(MockResponse().setBody("""{"something":"else"}"""))
        assertEquals(AppError.NotXtreamServer, (xtream().authenticate(c) as AppResult.Failure).error)
        server.enqueue(MockResponse().setBody("[]"))
        assertEquals(AppError.InvalidCredentials, (xtream().authenticate(c) as AppResult.Failure).error)
        server.shutdown()
    }

    @Test fun timeout_isMapped() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val r = xtream(testClient(readTimeoutMs = 300)).authenticate(XtreamCredentials(server.url("/").toString(), "u", "p"))
        assertEquals(AppError.Timeout, (r as AppResult.Failure).error)
        server.shutdown()
    }

    @Test fun unreachableServer_offlineVsUnreachable() = runBlocking {
        val server = MockWebServer().apply { start() }
        val url = server.url("/").toString()
        server.shutdown() // nothing listens on that port any more
        val unreachable = xtream(online = true).authenticate(XtreamCredentials(url, "u", "p"))
        assertEquals(AppError.ServerUnreachable, (unreachable as AppResult.Failure).error)
        val offline = xtream(online = false).authenticate(XtreamCredentials(url, "u", "p"))
        assertEquals(AppError.Offline, (offline as AppResult.Failure).error)
    }

    @Test fun lists_parseInconsistentTypes() = runBlocking {
        val ds = xtream()
        val live = (ds.liveStreams(creds()) as AppResult.Success).data
        assertEquals(listOf(101L, 102L, 103L), live.map { it.streamId })
        assertEquals("2", live[1].categoryId)
        assertEquals(1L, live[2].isAdult)
        val cats = (ds.liveCategories(creds()) as AppResult.Success).data
        assertEquals("2", cats[1].categoryId) // numeric id coerced to string
        val vod = (ds.vodStreams(creds()) as AppResult.Success).data
        assertEquals(8.1, vod[0].rating!!, 0.001)
        assertNull(vod[1].rating) // empty string rating
        assertEquals(6.0, vod[2].rating!!, 0.001)
        val series = (ds.series(creds()) as AppResult.Success).data
        assertEquals(listOf("http://img.local/b1.jpg"), series[0].backdropPath)
        assertTrue(series[1].backdropPath.isEmpty())
        assertEquals("get_live_streams", fake.requests.first { it.requestUrl!!.queryParameter("action") == "get_live_streams" }.requestUrl!!.queryParameter("action"))
    }

    @Test fun lists_emptyShapes_areEmpty() = runBlocking {
        for (body in listOf("[]", "{}", "null", "false", "", "  \n[ ]")) {
            fake.liveStreams = body
            val r = xtream().liveStreams(creds())
            assertTrue("body=$body", r is AppResult.Success && r.data.isEmpty())
        }
    }

    @Test fun serverError_onList_isFailure() = runBlocking {
        fake.failAction = "get_vod_streams"
        assertEquals(AppError.Server(500), (xtream().vodStreams(creds()) as AppResult.Failure).error)
    }

    @Test fun redactor_hidesCredentials() {
        val url = "http://h:8080/player_api.php?username=alice&password=p%40ss&action=get_vod_streams"
        val red = Redactor.redact(url)
        assertFalse(red.contains("alice")); assertFalse(red.contains("p%40ss"))
        assertTrue(red.contains("action=get_vod_streams"))
        val stream = Redactor.redact("http://h:8080/live/alice/secret/101.m3u8")
        assertEquals("http://h:8080/live/***/***/101.m3u8", stream)
    }
}
