package com.akaaflix.prime.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.data.backup.BackupManager
import com.akaaflix.prime.data.repository.ContentRulesRepositoryImpl
import com.akaaflix.prime.data.repository.LibraryRepositoryImpl
import com.akaaflix.prime.data.repository.PlaylistRepositoryImpl
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.domain.model.ContentRef
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.domain.repository.PlaylistInput
import com.akaaflix.prime.testutil.FakeCipher
import com.akaaflix.prime.testutil.FakeXtreamServer
import com.akaaflix.prime.testutil.inMemoryDb
import com.akaaflix.prime.testutil.xtream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun env(db: AppDatabase, name: String): Triple<PlaylistRepositoryImpl, SettingsRepository, BackupManager> {
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { tmp.newFile("$name.preferences_pb") })
        val playlists = PlaylistRepositoryImpl(db, FakeCipher(), xtream())
        return Triple(playlists, settings, BackupManager(db, FakeCipher(), settings, playlists))
    }

    @Test fun exportThenRestoreOnFreshDevice() = runBlocking {
        val fake = FakeXtreamServer(); val base = fake.start()
        val dbA = inMemoryDb()
        val (playlistsA, settingsA, backupA) = env(dbA, "a")
        val pid = (playlistsA.addPlaylist(PlaylistInput("Demo", "demo_user", "s3cret!", base)) as AppResult.Success).data
        val lib = LibraryRepositoryImpl(dbA)
        lib.addFavorite(ContentRef(pid, ContentType.MOVIE, "201", "Desert Planet", "http://img.local/m1.jpg"))
        val g = (lib.createGroup(pid, "Weekend") as AppResult.Success).data
        lib.addToGroup(g, ContentRef(pid, ContentType.SERIES, "301", "Kingdom Tales", null))
        ContentRulesRepositoryImpl(dbA).addRule(pid, RuleScope.CATEGORY, RuleRestriction.HIDDEN, ContentType.LIVE, "3", "Adults")
        settingsA.setPin("9999")

        // Without credentials the file contains no secrets.
        val noCreds = ByteArrayOutputStream().also { backupA.export(it, includeCredentials = false) }.toString(Charsets.UTF_8.name())
        assertFalse(noCreds.contains("s3cret!"))
        assertFalse(noCreds.contains("pin_hash"))

        val withCreds = ByteArrayOutputStream().also { assertTrue(backupA.export(it, includeCredentials = true) is AppResult.Success) }.toByteArray()

        val dbB = inMemoryDb()
        val (playlistsB, _, backupB) = env(dbB, "b")
        val report = (backupB.restore(ByteArrayInputStream(withCreds)) as AppResult.Success).data
        assertEquals(1, report.playlistsAdded)
        assertEquals(1, report.favorites)
        assertEquals(1, report.groups)
        assertEquals(1, report.rules)
        val newPid = playlistsB.observePlaylists().first().single().id
        assertEquals("s3cret!", playlistsB.credentials(newPid)!!.password)
        val libB = LibraryRepositoryImpl(dbB)
        assertEquals("Desert Planet", libB.observeFavorites(newPid).first().single().ref.title)
        assertEquals(1, libB.observeGroups(newPid).first().single().itemCount)

        // Garbage is rejected.
        assertTrue(backupB.restore(ByteArrayInputStream("{\"format\":\"other\"}".toByteArray())) is AppResult.Failure)
        dbA.close(); dbB.close(); fake.shutdown()
    }
}
