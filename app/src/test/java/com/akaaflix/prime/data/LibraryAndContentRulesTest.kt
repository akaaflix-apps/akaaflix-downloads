package com.akaaflix.prime.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.data.repository.CatalogRepositoryImpl
import com.akaaflix.prime.data.repository.CatalogSyncer
import com.akaaflix.prime.data.repository.ContentRulesRepositoryImpl
import com.akaaflix.prime.data.repository.HistoryRepositoryImpl
import com.akaaflix.prime.data.repository.LibraryRepositoryImpl
import com.akaaflix.prime.data.repository.PlaylistRepositoryImpl
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.domain.model.ContentRef
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.PlaybackEntry
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.domain.model.SortOrder
import com.akaaflix.prime.domain.repository.BrowseQuery
import com.akaaflix.prime.domain.repository.PlaylistInput
import com.akaaflix.prime.testutil.FakeCipher
import com.akaaflix.prime.testutil.FakeXtreamServer
import com.akaaflix.prime.testutil.inMemoryDb
import com.akaaflix.prime.testutil.xtream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Favorites, custom groups, hidden content and parental locks against a synced catalog. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LibraryAndContentRulesTest {
    private lateinit var fake: FakeXtreamServer
    private lateinit var db: AppDatabase
    private lateinit var catalog: CatalogRepositoryImpl
    private lateinit var library: LibraryRepositoryImpl
    private lateinit var rules: ContentRulesRepositoryImpl
    private lateinit var syncer: CatalogSyncer
    private var pid = 0L

    @Before fun setUp() = runBlocking {
        fake = FakeXtreamServer(); val base = fake.start()
        db = inMemoryDb()
        val ds = xtream()
        val playlists = PlaylistRepositoryImpl(db, FakeCipher(), ds)
        pid = (playlists.addPlaylist(PlaylistInput("Demo", "demo_user", "s3cret!", base)) as AppResult.Success).data
        syncer = CatalogSyncer(db, playlists, ds)
        assertTrue(syncer.sync(pid) is AppResult.Success)
        catalog = CatalogRepositoryImpl(db, playlists, ds)
        library = LibraryRepositoryImpl(db)
        rules = ContentRulesRepositoryImpl(db)
    }

    @After fun tearDown() { db.close(); fake.shutdown() }

    private fun q(type: ContentType, adult: Boolean = true) = BrowseQuery(pid, type, includeAdult = adult)
    private suspend fun movieIds(query: BrowseQuery = q(ContentType.MOVIE)) = catalog.movies(query).first().map { it.streamId }
    private suspend fun channelIds(query: BrowseQuery = q(ContentType.LIVE)) = catalog.channels(query).first().map { it.streamId }

    @Test fun favorites_unifiedAcrossTypes_andToggle() = runBlocking {
        val movie = ContentRef(pid, ContentType.MOVIE, "201", "Desert Planet", null)
        val channel = ContentRef(pid, ContentType.LIVE, "101", "Al News HD", null)
        val series = ContentRef(pid, ContentType.SERIES, "301", "Kingdom Tales", null)
        assertTrue(library.toggleFavorite(movie))
        library.addFavorite(channel); library.addFavorite(channel) // no duplicates
        library.addFavorite(series)
        assertEquals(3, library.observeFavorites(pid).first().size)
        assertEquals(setOf("101"), library.observeFavoriteIds(pid, ContentType.LIVE).first())
        assertTrue(library.isFavorite(pid, ContentType.MOVIE, "201").first())
        assertFalse(library.toggleFavorite(movie))
        assertFalse(library.isFavorite(pid, ContentType.MOVIE, "201").first())
        // Favorites-only browse query.
        library.addFavorite(movie)
        assertEquals(listOf(201L), movieIds(q(ContentType.MOVIE).copy(favoritesOnly = true)))
    }

    @Test fun groups_create_rename_mixContent_delete() = runBlocking {
        val g = (library.createGroup(pid, "Family") as AppResult.Success).data
        assertEquals(AppError.Duplicate, (library.createGroup(pid, "family") as AppResult.Failure).error)
        library.addToGroup(g, ContentRef(pid, ContentType.LIVE, "102", "Cartoon Fun", null))
        library.addToGroup(g, ContentRef(pid, ContentType.MOVIE, "202", "Quiet Harbor", null))
        library.addToGroup(g, ContentRef(pid, ContentType.SERIES, "302", "City Lights", null))
        library.addToGroup(g, ContentRef(pid, ContentType.SERIES, "302", "City Lights", null))
        assertEquals(3, library.observeGroupItems(g).first().size)
        assertEquals(3, library.observeGroups(pid).first().single().itemCount)
        assertEquals(setOf(g), library.observeGroupsContaining(pid, ContentType.MOVIE, "202").first())
        assertEquals(listOf(202L), movieIds(q(ContentType.MOVIE).copy(groupId = g)))

        assertTrue(library.renameGroup(g, "Kids & Family") is AppResult.Success)
        assertEquals("Kids & Family", library.observeGroups(pid).first().single().name)

        library.removeFromGroup(g, ContentType.MOVIE, "202")
        assertEquals(2, library.observeGroupItems(g).first().size)
        library.deleteGroup(g)
        assertTrue(library.observeGroups(pid).first().isEmpty())
        assertTrue(library.observeGroupItems(g).first().isEmpty())
    }

    @Test fun userData_survivesCatalogRefresh() = runBlocking {
        library.addFavorite(ContentRef(pid, ContentType.MOVIE, "201", "Desert Planet", null))
        val g = (library.createGroup(pid, "Keep") as AppResult.Success).data
        library.addToGroup(g, ContentRef(pid, ContentType.LIVE, "101", "Al News HD", null))
        rules.addRule(pid, RuleScope.ITEM, RuleRestriction.HIDDEN, ContentType.MOVIE, "203", "Old Classic")
        assertTrue(syncer.sync(pid) is AppResult.Success)
        assertEquals(1, library.observeFavorites(pid).first().size)
        assertEquals(1, library.observeGroupItems(g).first().size)
        assertFalse(203L in movieIds())
    }

    @Test fun hiddenItemsCategoriesAndKeywords_disappearFromBrowseAndSearch() = runBlocking {
        assertEquals(listOf(201L, 202L, 203L), movieIds())
        rules.addRule(pid, RuleScope.ITEM, RuleRestriction.HIDDEN, ContentType.MOVIE, "201", "Desert Planet")
        assertEquals(listOf(202L, 203L), movieIds())
        assertTrue(catalog.movies(q(ContentType.MOVIE).copy(search = "desert")).first().isEmpty())

        rules.addRule(pid, RuleScope.CATEGORY, RuleRestriction.HIDDEN, ContentType.MOVIE, "11", "Drama")
        assertTrue(movieIds().isEmpty())
        assertFalse(catalog.observeCategories(pid, ContentType.MOVIE, true).first().any { it.id == "11" })

        rules.addRule(pid, RuleScope.KEYWORD, RuleRestriction.HIDDEN, null, "cartoon", "cartoon")
        assertFalse(102L in channelIds())

        // Restore everything.
        rules.clear(pid, RuleRestriction.HIDDEN)
        assertEquals(listOf(201L, 202L, 203L), movieIds())
        assertTrue(102L in channelIds())
    }

    @Test fun adultContent_hiddenUnlessAllowed() = runBlocking {
        assertTrue(103L in channelIds(q(ContentType.LIVE, adult = true)))
        val visible = channelIds(q(ContentType.LIVE, adult = false))
        assertFalse(103L in visible) // flagged is_adult and in an adult-named category
        assertFalse(catalog.observeCategories(pid, ContentType.LIVE, false).first().any { it.id == "3" })
    }

    @Test fun pinLockedContent_isDetected() = runBlocking {
        assertFalse(rules.isLocked(pid, ContentType.MOVIE, "202", "11"))
        rules.addRule(pid, RuleScope.ITEM, RuleRestriction.PIN_LOCKED, ContentType.MOVIE, "202", "Quiet Harbor")
        assertTrue(rules.isLocked(pid, ContentType.MOVIE, "202", "11"))
        rules.addRule(pid, RuleScope.CATEGORY, RuleRestriction.PIN_LOCKED, ContentType.LIVE, "2", "Kids")
        assertTrue(rules.isLocked(pid, ContentType.LIVE, "102", "2"))
        // Locked content is still listed (with a lock) — only hidden content is removed.
        assertTrue(102L in channelIds())
        assertTrue(catalog.observeCategories(pid, ContentType.LIVE, true).first().first { it.id == "2" }.isLocked)
    }

    @Test fun sortingAndCategoryCounts() = runBlocking {
        assertEquals(listOf(201L, 202L, 203L), movieIds(q(ContentType.MOVIE).copy(sort = SortOrder.LATEST)))
        assertEquals(listOf(201L, 203L, 202L), movieIds(q(ContentType.MOVIE).copy(sort = SortOrder.RATING)))
        assertEquals(listOf(201L, 203L, 202L), movieIds(q(ContentType.MOVIE).copy(sort = SortOrder.NAME_ASC)))
        assertEquals(listOf(202L, 203L, 201L), movieIds(q(ContentType.MOVIE).copy(sort = SortOrder.NAME_DESC)))
        val counts = catalog.observeCategories(pid, ContentType.MOVIE, true).first().associate { it.id to it.count }
        assertEquals(mapOf("10" to 1, "11" to 2), counts)
    }

    @Test fun mostWatched_usesLocalHistory() = runBlocking {
        val history = HistoryRepositoryImpl(db)
        fun entry(id: String) = PlaybackEntry(pid, ContentType.MOVIE, id, "t", null, null, 60_000, 600_000, System.currentTimeMillis(), 1, null, null, null, "mp4", null)
        history.save(entry("203"), incrementPlayCount = true)
        history.save(entry("203"), incrementPlayCount = true)
        history.save(entry("202"), incrementPlayCount = true)
        assertEquals(listOf(203L, 202L), movieIds(q(ContentType.MOVIE).copy(mostWatched = true)))
        assertEquals(2, history.get(pid, ContentType.MOVIE, "203")!!.playCount)
        // 60 s of 10 min watched => both movies are in "Continue watching".
        assertEquals(setOf("203", "202"), history.observeContinueWatching(pid).first().map { it.contentId }.toSet())
    }
}
