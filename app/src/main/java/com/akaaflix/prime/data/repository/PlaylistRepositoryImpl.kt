package com.akaaflix.prime.data.repository

import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.database.entity.PlaylistEntity
import com.akaaflix.prime.domain.model.AccountInfo
import com.akaaflix.prime.domain.model.AccountStatus
import com.akaaflix.prime.domain.model.ConnectionStatus
import com.akaaflix.prime.domain.model.Playlist
import com.akaaflix.prime.domain.model.XtreamCredentials
import com.akaaflix.prime.domain.repository.PlaylistInput
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.domain.usecase.PlaylistValidator
import com.akaaflix.prime.network.ServerUrl
import com.akaaflix.prime.network.XtreamDataSource
import com.akaaflix.prime.security.CredentialCipher
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

class PlaylistRepositoryImpl(
    private val db: AppDatabase,
    private val cipher: CredentialCipher,
    private val xtream: XtreamDataSource,
    private val now: () -> Long = System::currentTimeMillis,
) : PlaylistRepository {

    private val dao = db.playlistDao()

    /** Decrypted credentials cached in memory only for the process lifetime. */
    private val credentialCache = ConcurrentHashMap<Long, XtreamCredentials>()

    override fun observePlaylists(): Flow<List<Playlist>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<Playlist?> = dao.observeActive().map { it?.toDomain() }

    override suspend fun getPlaylist(id: Long): Playlist? = dao.getById(id)?.toDomain()

    override suspend fun count(): Int = dao.count()

    override suspend fun testConnection(input: PlaylistInput): AppResult<AccountInfo> {
        val invalid = PlaylistValidator.validate(input.copy(name = input.name.ifBlank { "-" }))
        if (PlaylistValidator.Field.URL in invalid) return AppResult.Failure(AppError.InvalidUrl)
        if (invalid.isNotEmpty()) return AppResult.Failure(AppError.InvalidCredentials)
        val normalized = ServerUrl.normalize(input.url) ?: return AppResult.Failure(AppError.InvalidUrl)
        return authenticate(normalized, input.username.trim(), input.password)
    }

    /**
     * Authenticates. When the user did not type a scheme we try HTTPS first (preferred when
     * supported) and fall back to HTTP, which most Xtream panels still use.
     */
    private suspend fun authenticate(normalized: ServerUrl.Normalized, username: String, password: String): AppResult<AccountInfo> {
        if (!normalized.schemeWasGuessed) {
            return xtream.authenticate(XtreamCredentials(normalized.baseUrl, username, password))
        }
        val httpsUrl = ServerUrl.withScheme(normalized.baseUrl, "https")
        if (httpsUrl != null) {
            val https = xtream.authenticate(XtreamCredentials(httpsUrl, username, password), fastConnect = true)
            if (https is AppResult.Success) return https
            val err = (https as AppResult.Failure).error
            // Credential / account errors are definitive — no point retrying over HTTP.
            if (err is AppError.InvalidCredentials || err is AppError.AccountExpired || err is AppError.AccountBanned || err is AppError.AccountDisabled) return https
        }
        val httpUrl = ServerUrl.withScheme(normalized.baseUrl, "http") ?: return AppResult.Failure(AppError.InvalidUrl)
        return xtream.authenticate(XtreamCredentials(httpUrl, username, password))
    }

    override suspend fun addPlaylist(input: PlaylistInput): AppResult<Long> {
        val auth = testConnection(input)
        val info = when (auth) {
            is AppResult.Failure -> return auth
            is AppResult.Success -> auth.data
        }
        val username = input.username.trim()
        if (isDuplicate(info.resolvedBaseUrl, username, excludeId = null)) return AppResult.Failure(AppError.Duplicate)
        return try {
            val id = db.withTransaction {
                val isFirst = dao.count() == 0
                val entity = PlaylistEntity(
                    name = input.name.trim(),
                    serverUrl = info.resolvedBaseUrl,
                    usernameEnc = cipher.encrypt(username),
                    passwordEnc = cipher.encrypt(input.password),
                    isDefault = isFirst,
                    createdAt = now(),
                ).withAccount(info, now())
                val newId = dao.insert(entity)
                dao.setActive(newId)
                newId
            }
            credentialCache[id] = XtreamCredentials(info.resolvedBaseUrl, username, input.password)
            AppResult.Success(id)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage)
        }
    }

    override suspend fun updatePlaylist(id: Long, input: PlaylistInput): AppResult<Unit> {
        val existing = dao.getById(id) ?: return AppResult.Failure(AppError.NotFound)
        val password = input.password.ifEmpty { runCatching { cipher.decrypt(existing.passwordEnc) }.getOrNull() ?: return AppResult.Failure(AppError.Storage) }
        val effective = input.copy(password = password)
        val auth = testConnection(effective)
        val info = when (auth) {
            is AppResult.Failure -> return auth
            is AppResult.Success -> auth.data
        }
        val username = input.username.trim()
        if (isDuplicate(info.resolvedBaseUrl, username, excludeId = id)) return AppResult.Failure(AppError.Duplicate)
        val serverChanged = existing.serverUrl != info.resolvedBaseUrl
        dao.update(
            existing.copy(
                name = input.name.trim(),
                serverUrl = info.resolvedBaseUrl,
                usernameEnc = cipher.encrypt(username),
                passwordEnc = cipher.encrypt(password),
                lastRefreshAt = if (serverChanged) null else existing.lastRefreshAt,
            ).withAccount(info, now()),
        )
        credentialCache[id] = XtreamCredentials(info.resolvedBaseUrl, username, password)
        return AppResult.Success(Unit)
    }

    override suspend fun deletePlaylist(id: Long) {
        val wasActive = dao.getById(id)?.isActive == true
        val wasDefault = dao.getById(id)?.isDefault == true
        db.withTransaction {
            db.catalogDao().apply {
                deleteChannels(id); deleteMovies(id); deleteSeries(id); deleteAllCategories(id)
            }
            db.libraryDao().apply { deleteFavoritesForPlaylist(id); deleteGroupsForPlaylist(id) }
            db.hiddenContentDao().deleteForPlaylist(id)
            db.playbackHistoryDao().clear(id)
            db.userSettingsDao().clear(id)
            dao.deleteById(id)
            val remaining = dao.getAll()
            if (wasDefault && remaining.isNotEmpty()) dao.setDefault(remaining.first().id)
            if (wasActive && remaining.size == 1) dao.setActive(remaining.first().id)
        }
        credentialCache.remove(id)
    }

    override suspend fun setActive(id: Long) = dao.setActive(id)
    override suspend fun setDefault(id: Long) = dao.setDefault(id)
    override suspend fun clearActive() = dao.clearActive()

    override suspend fun refreshAccount(id: Long): AppResult<AccountInfo> {
        val entity = dao.getById(id) ?: return AppResult.Failure(AppError.NotFound)
        val creds = credentials(id) ?: return AppResult.Failure(AppError.Storage)
        val result = xtream.authenticate(creds)
        val latest = dao.getById(id) ?: return result
        when (result) {
            is AppResult.Success -> dao.update(latest.withAccount(result.data, now()))
            is AppResult.Failure -> {
                val status = when (result.error) {
                    AppError.AccountExpired -> AccountStatus.EXPIRED.name
                    AppError.AccountBanned -> AccountStatus.BANNED.name
                    AppError.AccountDisabled -> AccountStatus.DISABLED.name
                    else -> entity.accountStatus
                }
                val reachable = result.error is AppError.AccountExpired || result.error is AppError.AccountBanned ||
                    result.error is AppError.AccountDisabled || result.error is AppError.InvalidCredentials
                dao.update(
                    latest.copy(
                        accountStatus = status,
                        connectionStatus = if (reachable) ConnectionStatus.CONNECTED.name else ConnectionStatus.DISCONNECTED.name,
                        lastCheckedAt = now(),
                    ),
                )
            }
        }
        return result
    }

    override suspend fun credentials(id: Long): XtreamCredentials? {
        credentialCache[id]?.let { return it }
        val entity = dao.getById(id) ?: return null
        val creds = runCatching {
            XtreamCredentials(entity.serverUrl, cipher.decrypt(entity.usernameEnc), cipher.decrypt(entity.passwordEnc))
        }.getOrNull() ?: return null
        credentialCache[id] = creds
        return creds
    }

    private suspend fun isDuplicate(baseUrl: String, username: String, excludeId: Long?): Boolean =
        dao.getAll().any { e ->
            e.id != excludeId && sameServer(e.serverUrl, baseUrl) &&
                runCatching { cipher.decrypt(e.usernameEnc) }.getOrNull() == username
        }

    private fun sameServer(a: String, b: String): Boolean =
        a.substringAfter("://").trimEnd('/').equals(b.substringAfter("://").trimEnd('/'), ignoreCase = true)

    private fun PlaylistEntity.withAccount(info: AccountInfo, at: Long) = copy(
        connectionStatus = ConnectionStatus.CONNECTED.name,
        accountStatus = info.status.name,
        expDate = info.expiresAtEpochSec,
        serverCreatedAt = info.createdAtEpochSec,
        isTrial = info.isTrial,
        maxConnections = info.maxConnections,
        activeConnections = info.activeConnections,
        allowedFormats = info.allowedOutputFormats.joinToString(","),
        serverMessage = info.message,
        serverTimezone = info.serverTimezone,
        rtmpPort = info.rtmpPort,
        lastCheckedAt = at,
    )

    private fun PlaylistEntity.toDomain(): Playlist = Playlist(
        id = id,
        name = name,
        serverUrl = serverUrl,
        username = credentialCache[id]?.username ?: runCatching { cipher.decrypt(usernameEnc) }.getOrDefault(""),
        isActive = isActive,
        isDefault = isDefault,
        createdAt = createdAt,
        lastRefreshAt = lastRefreshAt,
        lastCheckedAt = lastCheckedAt,
        connectionStatus = runCatching { ConnectionStatus.valueOf(connectionStatus) }.getOrDefault(ConnectionStatus.UNKNOWN),
        accountStatus = runCatching { AccountStatus.valueOf(accountStatus) }.getOrDefault(AccountStatus.UNKNOWN),
        expiresAtEpochSec = expDate,
        createdAtEpochSec = serverCreatedAt,
        isTrial = isTrial,
        maxConnections = maxConnections,
        activeConnections = activeConnections,
        allowedOutputFormats = allowedFormats.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        serverMessage = serverMessage,
        serverTimezone = serverTimezone,
        rtmpPort = rtmpPort,
        liveCount = liveCount,
        movieCount = movieCount,
        seriesCount = seriesCount,
    )
}
