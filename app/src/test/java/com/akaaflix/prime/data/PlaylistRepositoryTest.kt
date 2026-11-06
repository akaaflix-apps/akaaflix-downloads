package com.akaaflix.prime.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.data.repository.LibraryRepositoryImpl
import com.akaaflix.prime.data.repository.PlaylistRepositoryImpl
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.domain.model.AccountStatus
import com.akaaflix.prime.domain.model.ContentRef
import com.akaaflix.prime.domain.model.ContentType
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlaylistRepositoryTest {
    private lateinit var fake: FakeXtreamServer
    private lateinit var base: String
    private lateinit var db: AppDatabase
    private lateinit var repo: PlaylistRepositoryImpl

    @Before fun setUp() {
        fake = FakeXtreamServer(); base = fake.start()
        db = inMemoryDb()
        repo = PlaylistRepositoryImpl(db, FakeCipher(), xtream())
    }

    @After fun tearDown() { db.close(); fake.shutdown() }

    private fun input(name: String = "Nova IPTV", user: String = "demo_user", pass: String = "s3cret!", url: String = base) = PlaylistInput(name, user, pass, url)

    @Test fun addPlaylist_authenticates_encrypts_andActivates() = runBlocking {
        val r = repo.addPlaylist(input())
        assertTrue(r is AppResult.Success)
        val id = (r as AppResult.Success).data
        val entity = db.playlistDao().getById(id)!!
        // Stored credentials are not plain text.
        assertNotEquals("demo_user", entity.usernameEnc)
        assertNotEquals("s3cret!", entity.passwordEnc)
        assertFalse(entity.passwordEnc.contains("s3cret"))
        val p = repo.getPlaylist(id)!!
        assertEquals("Nova IPTV", p.name)
        assertEquals("demo_user", p.username)
        assertTrue(p.isActive); assertTrue(p.isDefault)
        assertEquals(AccountStatus.ACTIVE, p.accountStatus)
        assertEquals(1893456000L, p.expiresAtEpochSec)
        assertEquals(2, p.maxConnections)
        assertEquals("s3cret!", repo.credentials(id)!!.password)
        assertEquals(p, repo.observeActive().first())
    }

    @Test fun addPlaylist_rejectsBadCredentialsAndDuplicates() = runBlocking {
        val bad = repo.addPlaylist(input(pass = "wrong"))
        assertEquals(AppError.InvalidCredentials, (bad as AppResult.Failure).error)
        assertEquals(0, repo.count())
        assertTrue(repo.addPlaylist(input()) is AppResult.Success)
        val dup = repo.addPlaylist(input(name = "Again"))
        assertEquals(AppError.Duplicate, (dup as AppResult.Failure).error)
        assertEquals(1, repo.count())
    }

    @Test fun addPlaylist_invalidUrl() = runBlocking {
        assertEquals(AppError.InvalidUrl, (repo.addPlaylist(input(url = "not a url")) as AppResult.Failure).error)
    }

    @Test fun editPlaylist_renamesAndKeepsPasswordWhenBlank() = runBlocking {
        val id = (repo.addPlaylist(input()) as AppResult.Success).data
        val r = repo.updatePlaylist(id, input(name = "Renamed", pass = ""))
        assertTrue(r is AppResult.Success)
        assertEquals("Renamed", repo.getPlaylist(id)!!.name)
        assertEquals("s3cret!", repo.credentials(id)!!.password)
        // A wrong new password is rejected and nothing changes.
        val bad = repo.updatePlaylist(id, input(name = "X", pass = "wrong"))
        assertTrue(bad is AppResult.Failure)
        assertEquals("Renamed", repo.getPlaylist(id)!!.name)
    }

    @Test fun deletePlaylist_removesItsUserData_andPromotesRemaining() = runBlocking {
        val first = (repo.addPlaylist(input()) as AppResult.Success).data
        val fake2 = FakeXtreamServer(username = "other", password = "pw2"); val base2 = fake2.start()
        val second = (repo.addPlaylist(PlaylistInput("Second", "other", "pw2", base2)) as AppResult.Success).data
        val lib = LibraryRepositoryImpl(db)
        lib.addFavorite(ContentRef(first, ContentType.MOVIE, "201", "Desert Planet", null))
        val g = (lib.createGroup(first, "Weekend") as AppResult.Success).data
        lib.addToGroup(g, ContentRef(first, ContentType.LIVE, "101", "Al News", null))

        repo.deletePlaylist(second) // was active (most recently added)
        assertNull(repo.getPlaylist(second))
        assertTrue(repo.getPlaylist(first)!!.isActive)

        repo.deletePlaylist(first)
        assertEquals(0, repo.count())
        assertTrue(lib.observeFavorites(first).first().isEmpty())
        assertTrue(lib.observeGroups(first).first().isEmpty())
        fake2.shutdown()
    }

    @Test fun setActiveAndDefault_areExclusive() = runBlocking {
        val a = (repo.addPlaylist(input()) as AppResult.Success).data
        val fake2 = FakeXtreamServer(username = "other", password = "pw2"); val base2 = fake2.start()
        val b = (repo.addPlaylist(PlaylistInput("B", "other", "pw2", base2)) as AppResult.Success).data
        repo.setActive(a)
        repo.setDefault(b)
        val all = repo.observePlaylists().first()
        assertEquals(listOf(a), all.filter { it.isActive }.map { it.id })
        assertEquals(listOf(b), all.filter { it.isDefault }.map { it.id })
        repo.clearActive()
        assertNull(repo.observeActive().first())
        fake2.shutdown()
    }

    @Test fun refreshAccount_tracksExpiredStatus() = runBlocking {
        val id = (repo.addPlaylist(input()) as AppResult.Success).data
        fake.status = "Expired"
        val r = repo.refreshAccount(id)
        assertEquals(AppError.AccountExpired, (r as AppResult.Failure).error)
        assertEquals(AccountStatus.EXPIRED, repo.getPlaylist(id)!!.accountStatus)
    }
}
