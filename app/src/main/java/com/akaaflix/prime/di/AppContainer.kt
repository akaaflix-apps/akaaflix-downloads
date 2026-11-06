package com.akaaflix.prime.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.data.backup.BackupManager
import com.akaaflix.prime.data.repository.CatalogRepositoryImpl
import com.akaaflix.prime.data.repository.CatalogSyncer
import com.akaaflix.prime.data.repository.ContentRulesRepositoryImpl
import com.akaaflix.prime.data.repository.EpgRepositoryImpl
import com.akaaflix.prime.data.repository.HistoryRepositoryImpl
import com.akaaflix.prime.data.repository.LibraryRepositoryImpl
import com.akaaflix.prime.data.repository.NotificationRepository
import com.akaaflix.prime.data.repository.PlaylistPrefs
import com.akaaflix.prime.data.repository.PlaylistRepositoryImpl
import com.akaaflix.prime.data.repository.SearchHistoryRepository
import com.akaaflix.prime.data.settings.LocaleMirror
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.domain.repository.CatalogRepository
import com.akaaflix.prime.domain.repository.ContentRulesRepository
import com.akaaflix.prime.domain.repository.EpgRepository
import com.akaaflix.prime.domain.repository.HistoryRepository
import com.akaaflix.prime.domain.repository.LibraryRepository
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.network.NetworkModule
import com.akaaflix.prime.network.XtreamApiFactory
import com.akaaflix.prime.network.XtreamDataSource
import com.akaaflix.prime.player.ExternalPlayers
import com.akaaflix.prime.player.PlayerFactory
import com.akaaflix.prime.security.CredentialCipher
import com.akaaflix.prime.security.KeystoreCredentialCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "akaaflix_settings")

/**
 * Manual dependency container (Clean Architecture wiring). One instance per process,
 * owned by [com.akaaflix.prime.AkaaFlixApp].
 */
class AppContainer(context: Context, private val cipherOverride: CredentialCipher? = null) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val okHttpClient: OkHttpClient by lazy { NetworkModule.okHttpClient() }
    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val cipher: CredentialCipher by lazy { cipherOverride ?: KeystoreCredentialCipher() }
    val localeMirror = LocaleMirror(appContext)
    val settingsRepository by lazy { SettingsRepository(appContext.settingsDataStore, localeMirror) }

    val xtreamDataSource by lazy { XtreamDataSource(XtreamApiFactory(okHttpClient), isOnline = ::isOnline) }

    val playlistRepository: PlaylistRepository by lazy { PlaylistRepositoryImpl(database, cipher, xtreamDataSource) }
    val catalogSyncer by lazy { CatalogSyncer(database, playlistRepository, xtreamDataSource) }
    val catalogRepository: CatalogRepository by lazy { CatalogRepositoryImpl(database, playlistRepository, xtreamDataSource) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepositoryImpl(database) }
    val rulesRepository: ContentRulesRepository by lazy { ContentRulesRepositoryImpl(database) }
    val historyRepository: HistoryRepository by lazy { HistoryRepositoryImpl(database) }
    val epgRepository: EpgRepository by lazy {
        EpgRepositoryImpl(database, playlistRepository, xtreamDataSource, cacheHours = { settingsRepository.current().epgCacheHours })
    }
    val searchHistoryRepository by lazy { SearchHistoryRepository(database) }
    val notificationRepository by lazy { NotificationRepository(database) }
    val playlistPrefs by lazy { PlaylistPrefs(database) }
    val backupManager by lazy { BackupManager(database, cipher, settingsRepository, playlistRepository) }
    val sessionManager by lazy { SessionManager(playlistRepository, catalogSyncer, settingsRepository, appScope) }

    val playerFactory by lazy { PlayerFactory(appContext, okHttpClient) }
    val externalPlayers by lazy { ExternalPlayers(appContext) }

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
