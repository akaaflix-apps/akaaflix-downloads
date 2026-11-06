package com.akaaflix.prime.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.data.repository.CatalogRepositoryImpl
import com.akaaflix.prime.data.repository.CatalogSyncer
import com.akaaflix.prime.data.repository.EpgRepositoryImpl
import com.akaaflix.prime.data.repository.PlaylistRepositoryImpl
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.repository.PlaylistInput
import com.akaaflix.prime.network.XtreamJson
import com.akaaflix.prime.testutil.FakeCipher
import com.akaaflix.prime.testutil.FakeXtreamServer
import com.akaaflix.prime.testutil.inMemoryDb
import com.akaaflix.prime.testutil.xtream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CatalogSyncAndDetailsTest {
    private lateinit var fake: FakeXtreamServer
    private lateinit var db: AppDatabase
    private lateinit var playlists: PlaylistRepositoryImpl
    private lateinit var syncer: CatalogSyncer
    private lateinit var catalog: CatalogRepositoryImpl
    private var pid = 0L

    @Before fun setUp() = runBlocking {
        fake = FakeXtreamServer(); val base = fake.start()
        db = inMemoryDb()
        val ds = xtream()
        playlists = PlaylistRepositoryImpl(db, FakeCipher(), ds)
        pid = (playlists.addPlaylist(PlaylistInput("Demo", "demo_user", "s3cret!", base)) as AppResult.Success).data
        syncer = CatalogSyncer(db, playlists, ds)
        catalog = CatalogRepositoryImpl(db, playlists, ds)
    }

    @After fun tearDown() { db.close(); fake.shutdown() }

    @Test fun sync_storesCatalogAndCounts() = runBlocking {
        val summary = (syncer.sync(pid) as AppResult.Success).data
        assertEquals(3, summary.liveCount); assertEquals(3, summary.movieCount); assertEquals(2, summary.seriesCount)
        assertEquals(0, summary.newMovies) // first sync never reports "new"
        val p = playlists.getPlaylist(pid)!!
        assertEquals(3, p.liveCount); assertNotNull(p.lastRefreshAt)
        assertEquals(Triple(3, 3, 2), catalog.observeCounts(pid).first())
        val m = catalog.getMovie(pid, 201)!!
        assertEquals("4K", m.quality); assertEquals(2024, m.year); assertEquals("mkv", m.containerExtension)
        assertEquals(2021, catalog.getMovie(pid, 202)!!.year)
        assertNull(catalog.getChannel(pid, 102)!!.logo) // empty icon => placeholder
        assertEquals("http://img.local/b1.jpg", catalog.getSeries(pid, 301)!!.backdrop)
        assertTrue(catalog.allCategories(pid, ContentType.LIVE).first { it.id == "3" }.isAdult)
    }

    @Test fun resync_reportsNewContent() = runBlocking {
        syncer.sync(pid)
        fake.vodStreams = FakeXtreamServer.VOD_STREAMS.replace("]", """,{"name":"Brand New","stream_id":204,"added":"1800000000","category_id":"10","container_extension":"mp4"}]""")
        val summary = (syncer.sync(pid) as AppResult.Success).data
        assertEquals(1, summary.newMovies)
        assertEquals(4, summary.movieCount)
    }

    @Test fun partialFailure_keepsPreviousData() = runBlocking {
        syncer.sync(pid)
        fake.failAction = "get_vod_streams"
        val summary = (syncer.sync(pid) as AppResult.Success).data
        assertEquals(AppError.Server(500), summary.partialErrors.single())
        assertEquals(3, summary.movieCount)
        assertNotNull(catalog.getMovie(pid, 201))
    }

    @Test fun movieDetails_fromVodInfo() = runBlocking {
        syncer.sync(pid)
        val d = (catalog.movieDetails(pid, 201) as AppResult.Success).data
        assertEquals("A long journey across the dunes.", d.plot)
        assertEquals("Sci-Fi", d.genre)
        assertEquals(9000L, d.durationSec)
        assertEquals("16+", d.ageRating)
        assertEquals("http://img.local/bd.jpg", d.backdrop)
        assertEquals("Director X", d.director)
    }

    @Test fun seriesDetails_parsesSeasonsAndEpisodes() = runBlocking {
        syncer.sync(pid)
        val d = (catalog.seriesDetails(pid, 301) as AppResult.Success).data
        assertEquals(listOf(1, 2), d.seasons.map { it.number }) // declared empty season 5 is dropped
        assertEquals(listOf("9001", "9002"), d.episodesBySeason[1]!!.map { it.id })
        assertEquals(2700L, d.episodesBySeason[1]!!.first().durationSec)
        assertEquals("mp4", d.episodesBySeason[1]!![1].containerExtension)
        assertEquals(listOf("9001", "9002", "9003"), d.allEpisodesOrdered().map { it.id })
        assertEquals("A, B", d.cast)
    }

    @Test fun seriesEpisodes_arrayShape() {
        val element = (XtreamJson.parseToJsonElement(FakeXtreamServer.SERIES_INFO_ARRAY_EPISODES) as JsonObject)["episodes"]
        val parsed = catalog.parseEpisodes(1, element)
        assertEquals(listOf("7000", "7001"), parsed[1]!!.map { it.id })
    }

    @Test fun shortEpg_decodesBase64_andCaches() = runBlocking {
        syncer.sync(pid)
        val epg = EpgRepositoryImpl(db, playlists, xtream(), cacheHours = { 6 })
        val first = (epg.shortEpg(pid, 101) as AppResult.Success).data
        assertEquals(listOf("Morning News", "Evening Talk"), first.map { it.title })
        assertEquals("Daily headlines", first.first().description)
        val requestsBefore = fake.requests.size
        val cached = (epg.shortEpg(pid, 101) as AppResult.Success).data
        assertEquals(first, cached)
        assertEquals(requestsBefore, fake.requests.size) // served from Room cache
        assertEquals("Morning News", epg.cachedNowPlaying(pid, listOf(101, 102))[101]!!.title)
    }
}
