package com.akaaflix.prime.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.akaaflix.prime.data.settings.PlayerChoice

/** External players supported through standard Android `ACTION_VIEW` intents. */
data class ExternalPlayerInfo(val choice: PlayerChoice, val packages: List<String>, val installedPackage: String?) {
    val isInstalled: Boolean get() = choice == PlayerChoice.SYSTEM || installedPackage != null
}

class ExternalPlayers(private val context: Context) {

    private val candidates = mapOf(
        PlayerChoice.VLC to listOf("org.videolan.vlc", "org.videolan.vlc.betav7neon"),
        PlayerChoice.MX_PLAYER to listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.beta"),
    )

    /** Detects which external players are actually installed (never pretends). */
    fun detect(): List<ExternalPlayerInfo> = listOf(PlayerChoice.VLC, PlayerChoice.MX_PLAYER).map { choice ->
        val pkgs = candidates[choice].orEmpty()
        ExternalPlayerInfo(choice, pkgs, pkgs.firstOrNull { isInstalled(it) })
    } + ExternalPlayerInfo(PlayerChoice.SYSTEM, emptyList(), if (hasSystemVideoHandler()) "system" else null)

    fun isAvailable(choice: PlayerChoice): Boolean = when (choice) {
        PlayerChoice.BUILT_IN -> true
        PlayerChoice.SYSTEM -> hasSystemVideoHandler()
        else -> candidates[choice].orEmpty().any { isInstalled(it) }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun hasSystemVideoHandler(): Boolean {
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://example.invalid/v.mp4"), "video/*")
        val list = if (Build.VERSION.SDK_INT >= 33) context.packageManager.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
        else @Suppress("DEPRECATION") context.packageManager.queryIntentActivities(probe, 0)
        return list.any { it.activityInfo.packageName != context.packageName }
    }

    /**
     * Hands the stream to the chosen external player. Returns false if the player is missing.
     * Note: the stream URL necessarily contains the Xtream credentials; it is only passed to
     * the player the user explicitly selected.
     */
    fun launch(choice: PlayerChoice, source: StreamSource, title: String, startPositionMs: Long = 0): Boolean {
        val pkg = when (choice) {
            PlayerChoice.SYSTEM -> null
            PlayerChoice.BUILT_IN -> return false
            else -> candidates[choice].orEmpty().firstOrNull { isInstalled(it) } ?: return false
        }
        val mime = source.mimeType ?: "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(source.url), mime)
            pkg?.let { setPackage(it) }
            putExtra("title", title)
            putExtra("name", title)
            if (startPositionMs > 0) {
                putExtra("position", startPositionMs.toInt()) // MX Player
                putExtra("from_start", false) // VLC
            }
            putExtra("secure_uri", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (choice == PlayerChoice.SYSTEM) {
                context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                context.startActivity(intent)
            }
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
