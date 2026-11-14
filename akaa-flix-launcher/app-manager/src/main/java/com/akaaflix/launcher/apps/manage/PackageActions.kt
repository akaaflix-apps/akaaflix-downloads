package com.akaaflix.launcher.apps.manage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.ImageView
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.akaaflix.launcher.apps.privileged.PackageCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PackageAction { LAUNCH, APP_INFO, UNINSTALL, UNINSTALL_UPDATES, DISABLE, ENABLE, REMOVE_FOR_USER, RESTORE }

/** How an action is carried out on this device right now. */
enum class ActionPath {
    /** Normal app API or Android's own confirmation dialog (launch, App info, uninstall of user apps). */
    DIRECT,
    /** Through the user's Shizuku service (adb / root identity). */
    SHIZUKU,
    /** Android's App info screen, where the system decides whether the button is offered. */
    SYSTEM_SCREEN,
    /** Needs Shizuku or a computer with ADB; the exact command is shown. */
    ADB_ONLY,
}

data class ActionPlan(val action: PackageAction, val path: ActionPath, val command: List<String>? = null, val undo: List<String>? = null)

/** Which actions Android allows for a package and how (pure, unit tested). */
object PackageActions {
    fun plan(p: ManagedPackage, shizukuReady: Boolean, sdkInt: Int, user: Int = PackageCommands.currentUser()): List<ActionPlan> {
        val out = ArrayList<ActionPlan>()
        val pkg = p.packageName
        val critical = p.protection == Protection.CRITICAL
        val validName = PackageCommands.isValidPackage(pkg)
        if (p.installed && p.enabled && p.launchable) out += ActionPlan(PackageAction.LAUNCH, ActionPath.DIRECT)
        if (p.installed) out += ActionPlan(PackageAction.APP_INFO, ActionPath.DIRECT)
        if (p.installed && !p.isSystem && !critical) {
            // With Shizuku: uninstall for this user directly (confirmed in Akaa Flix); otherwise Android's own dialog.
            out += if (shizukuReady && validName) ActionPlan(PackageAction.UNINSTALL, ActionPath.SHIZUKU, PackageCommands.uninstall(pkg, user))
            else ActionPlan(PackageAction.UNINSTALL, ActionPath.DIRECT)
        }
        if (!validName || critical) return out
        if (p.installed && p.isUpdatedSystem) {
            out += if (shizukuReady && sdkInt >= 29) ActionPlan(PackageAction.UNINSTALL_UPDATES, ActionPath.SHIZUKU, PackageCommands.uninstallUpdates(pkg))
            else ActionPlan(PackageAction.UNINSTALL_UPDATES, ActionPath.SYSTEM_SCREEN, if (sdkInt >= 29) PackageCommands.uninstallUpdates(pkg) else null)
        }
        if (p.installed && p.enabled) {
            val cmd = PackageCommands.disable(pkg, user)
            out += ActionPlan(PackageAction.DISABLE, if (shizukuReady) ActionPath.SHIZUKU else ActionPath.SYSTEM_SCREEN, cmd, PackageCommands.enable(pkg, user))
        }
        if (p.installed && !p.enabled) {
            out += ActionPlan(PackageAction.ENABLE, if (shizukuReady) ActionPath.SHIZUKU else ActionPath.SYSTEM_SCREEN, PackageCommands.enable(pkg, user))
        }
        if (p.installed && p.isSystem) {
            out += ActionPlan(PackageAction.REMOVE_FOR_USER, if (shizukuReady) ActionPath.SHIZUKU else ActionPath.ADB_ONLY,
                PackageCommands.removeForUser(pkg, user), PackageCommands.restore(pkg, user))
        }
        if (!p.installed) {
            out += ActionPlan(PackageAction.RESTORE, if (shizukuReady) ActionPath.SHIZUKU else ActionPath.ADB_ONLY, PackageCommands.restore(pkg, user))
        }
        return out
    }
}

/** Icons for any package (also non-launchable and removed-for-user ones), cached in memory. */
object PackageIcons {
    private val cache = LruCache<String, Bitmap>(160)

    fun loader(scope: CoroutineScope, context: Context, pkg: String, sizePx: Int): (ImageView) -> Unit = { iv ->
        iv.tag = pkg
        val hit = cache.get(pkg)
        if (hit != null) iv.setImageBitmap(hit) else scope.launch {
            val bmp = withContext(Dispatchers.IO) { load(context, pkg, sizePx) }
            if (bmp != null) cache.put(pkg, bmp)
            if (iv.tag == pkg) iv.setImageBitmap(bmp)
        }
    }

    @Suppress("DEPRECATION")
    private fun load(context: Context, pkg: String, size: Int): Bitmap? = try {
        val pm = context.packageManager
        val ai = pm.getApplicationInfo(pkg, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES)
        val d = ai.loadIcon(pm)
        if (d.intrinsicWidth > 0) d.toBitmap(size, size, Bitmap.Config.ARGB_8888)
        else Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b -> d.setBounds(0, 0, size, size); d.draw(Canvas(b)) }
    } catch (_: Exception) { null }
}
