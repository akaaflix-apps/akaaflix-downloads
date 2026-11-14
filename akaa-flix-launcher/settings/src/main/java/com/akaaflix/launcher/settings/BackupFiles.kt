package com.akaaflix.launcher.settings

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Backup files that work without a document picker. Android TV / Google TV builds often ship no
 * document picker: their "framework package stubs" app answers the picker request, shows "You don't
 * have an app that can do this" and cancels. Backups are therefore written by the launcher itself:
 *  - always an internal copy (restorable at any time on this device, kept across updates);
 *  - a visible copy in Download/AkaaFlix (Android 10+, no permission needed) or, on Android 6–9, in
 *    the launcher's own folder on the shared storage, so it can be copied to another device.
 */
object BackupFiles {
    const val PREFIX = "AkaaFlixLauncher-backup-"
    const val FOLDER = "AkaaFlix"
    private const val KEEP = 10

    /** Packages that only stub out system screens on TV builds (no real picker behind them). */
    fun isStub(pkg: String?) = pkg.isNullOrEmpty() || pkg.contains("frameworkpackagestubs")

    /** True when a real app (not a TV stub) can handle [intent]. */
    fun hasRealHandler(c: Context, intent: Intent): Boolean = try {
        c.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).any { !isStub(it.activityInfo?.packageName) }
    } catch (_: Exception) { false }

    fun createDocumentIntent(name: String) = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType("application/json").putExtra(Intent.EXTRA_TITLE, name)
    fun openDocumentIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
    fun getContentIntent() = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")

    private fun internalDir(c: Context) = File(c.filesDir, "backups").apply { mkdirs() }

    /** Writes the backup; returns where the visible copy is (or the internal one), null on failure. */
    fun save(c: Context, name: String, json: String): String? {
        val internal = try { File(internalDir(c), name).also { it.writeText(json) } } catch (_: Exception) { null }
        prune(c)
        val visible = if (Build.VERSION.SDK_INT >= 29) saveToDownloads(c, name, json)
        else try { c.getExternalFilesDir(null)?.let { d -> File(d, name).also { it.writeText(json) }.absolutePath } } catch (_: Exception) { null }
        return visible ?: internal?.let { name }
    }

    /** Android 10+: Download/AkaaFlix/<name> through MediaStore (own file, no storage permission). */
    private fun saveToDownloads(c: Context, name: String, json: String): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val cr = c.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            }
            val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            cr.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: return null
            Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/" + name
        } catch (_: Exception) { null }
    }

    data class Entry(val name: String, val modified: Long, val read: () -> String?)

    /** Backups this installation can read: internal copies, own Download/AkaaFlix files, own folder. Newest first. */
    fun list(c: Context): List<Entry> {
        val out = LinkedHashMap<String, Entry>()
        fun addFiles(dir: File?) = dir?.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(".json") }?.forEach { f ->
            out.putIfAbsent(f.name, Entry(f.name, f.lastModified()) { try { f.readText() } catch (_: Exception) { null } })
        }
        addFiles(internalDir(c))
        if (Build.VERSION.SDK_INT >= 29) try {
            val cr = c.contentResolver
            val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            cr.query(uri, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED),
                MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?", arrayOf("$PREFIX%"), null)?.use { cur ->
                while (cur.moveToNext()) {
                    val n = cur.getString(1) ?: continue
                    val item = android.content.ContentUris.withAppendedId(uri, cur.getLong(0))
                    out.putIfAbsent(n, Entry(n, cur.getLong(2) * 1000) { try { cr.openInputStream(item)?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (_: Exception) { null } })
                }
            }
        } catch (_: Exception) { }
        try { addFiles(c.getExternalFilesDir(null)) } catch (_: Exception) { }
        return out.values.sortedByDescending { it.name }
    }

    /** Keeps the newest [KEEP] internal copies. */
    private fun prune(c: Context) {
        internalDir(c).listFiles { f -> f.name.startsWith(PREFIX) }?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
    }

    /** "AkaaFlixLauncher-backup-20261114-1830.json" → "2026-11-14 18:30" (pure, unit tested). */
    fun label(name: String): String {
        val m = Regex("""$PREFIX(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})""").find(name) ?: return name
        val (y, mo, d, h, mi) = m.destructured
        return "$y-$mo-$d $h:$mi"
    }
}
