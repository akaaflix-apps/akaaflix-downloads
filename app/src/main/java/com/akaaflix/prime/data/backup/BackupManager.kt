package com.akaaflix.prime.data.backup

import androidx.room.withTransaction
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.database.entity.CustomGroupEntity
import com.akaaflix.prime.database.entity.FavoriteEntity
import com.akaaflix.prime.database.entity.GroupContentEntity
import com.akaaflix.prime.database.entity.HiddenContentEntity
import com.akaaflix.prime.domain.repository.PlaylistInput
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.security.CredentialCipher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val includesCredentials: Boolean,
    val settings: Map<String, String> = emptyMap(),
    val playlists: List<BackupPlaylist> = emptyList(),
) {
    companion object { const val FORMAT = "akaaflix-prime-backup" }
}

@Serializable
data class BackupPlaylist(
    val name: String,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val favorites: List<BackupItem> = emptyList(),
    val groups: List<BackupGroup> = emptyList(),
    val rules: List<BackupRule> = emptyList(),
)

@Serializable
data class BackupItem(val type: String, val id: String, val title: String, val image: String? = null, val extra: String? = null)

@Serializable
data class BackupGroup(val name: String, val items: List<BackupItem> = emptyList())

@Serializable
data class BackupRule(val scope: String, val restriction: String, val type: String, val target: String, val title: String)

data class RestoreReport(val playlistsAdded: Int, val playlistsMatched: Int, val playlistsSkipped: Int, val favorites: Int, val groups: Int, val rules: Int)

/**
 * Local backup & restore to a user-chosen file (Storage Access Framework). Credentials are
 * only exported when the user explicitly opts in (they are written in plain text then, which
 * the UI warns about). Parental PIN hashes are never exported.
 */
class BackupManager(
    private val db: AppDatabase,
    private val cipher: CredentialCipher,
    private val settings: SettingsRepository,
    private val playlists: PlaylistRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(out: OutputStream, includeCredentials: Boolean): AppResult<Int> = try {
        val lib = db.libraryDao()
        val allFavorites = lib.allFavorites().groupBy { it.playlistId }
        val allGroups = lib.allGroups().groupBy { it.playlistId }
        val allGroupItems = lib.allGroupItems().groupBy { it.groupId }
        val allRules = db.hiddenContentDao().all().groupBy { it.playlistId }
        val items = db.playlistDao().getAll().map { p ->
            BackupPlaylist(
                name = p.name,
                url = p.serverUrl,
                username = if (includeCredentials) runCatching { cipher.decrypt(p.usernameEnc) }.getOrNull() else null,
                password = if (includeCredentials) runCatching { cipher.decrypt(p.passwordEnc) }.getOrNull() else null,
                favorites = allFavorites[p.id].orEmpty().map { BackupItem(it.contentType, it.contentId, it.title, it.imageUrl, it.extra) },
                groups = allGroups[p.id].orEmpty().map { g ->
                    BackupGroup(g.name, allGroupItems[g.id].orEmpty().map { BackupItem(it.contentType, it.contentId, it.title, it.imageUrl, it.extra) })
                },
                rules = allRules[p.id].orEmpty().map { BackupRule(it.scope, it.restriction, it.contentType, it.targetId, it.title) },
            )
        }
        val file = BackupFile(exportedAt = System.currentTimeMillis(), includesCredentials = includeCredentials, settings = settings.exportMap(), playlists = items)
        out.bufferedWriter(Charsets.UTF_8).use { it.write(json.encodeToString(BackupFile.serializer(), file)) }
        AppResult.Success(items.size)
    } catch (t: Throwable) {
        AppResult.Failure(AppError.Storage)
    }

    suspend fun restore(input: InputStream): AppResult<RestoreReport> {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (t: Throwable) {
            return AppResult.Failure(AppError.InvalidResponse)
        }
        if (file.format != BackupFile.FORMAT) return AppResult.Failure(AppError.InvalidResponse)
        settings.importMap(file.settings)

        var added = 0; var matched = 0; var skipped = 0; var favs = 0; var groups = 0; var rules = 0
        for (bp in file.playlists) {
            val existing = db.playlistDao().getAll().firstOrNull { it.serverUrl.equals(bp.url, true) && it.name == bp.name }
            val playlistId = when {
                existing != null -> { matched++; existing.id }
                bp.username != null && bp.password != null -> {
                    when (val r = playlists.addPlaylist(PlaylistInput(bp.name, bp.username, bp.password, bp.url))) {
                        is AppResult.Success -> { added++; r.data }
                        is AppResult.Failure -> { skipped++; null }
                    }
                }
                else -> { skipped++; null }
            } ?: continue
            db.withTransaction {
                val lib = db.libraryDao()
                bp.favorites.forEach { f ->
                    if (lib.insertFavorite(FavoriteEntity(playlistId = playlistId, contentType = f.type, contentId = f.id, title = f.title, imageUrl = f.image, extra = f.extra)) > 0) favs++
                }
                bp.groups.forEach { g ->
                    val gid = lib.findGroupByName(playlistId, g.name)?.id ?: lib.insertGroup(CustomGroupEntity(playlistId = playlistId, name = g.name)).also { groups++ }
                    g.items.forEach { i ->
                        lib.insertGroupItem(GroupContentEntity(groupId = gid, playlistId = playlistId, contentType = i.type, contentId = i.id, title = i.title, imageUrl = i.image, extra = i.extra))
                    }
                }
                bp.rules.forEach { r ->
                    if (db.hiddenContentDao().insert(HiddenContentEntity(playlistId = playlistId, scope = r.scope, restriction = r.restriction, contentType = r.type, targetId = r.target, title = r.title)) > 0) rules++
                }
            }
        }
        return AppResult.Success(RestoreReport(added, matched, skipped, favs, groups, rules))
    }
}
