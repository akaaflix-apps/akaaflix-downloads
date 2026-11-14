package com.akaaflix.launcher.apps

import com.akaaflix.launcher.apps.model.AppEntry
import java.util.Locale

/**
 * Removes duplicate launcher entries that Android legitimately reports but a TV launcher must not
 * show twice (pure logic, unit tested):
 *
 *  1. Other user profiles. Android 14 devices (and vendor "dual / clone app" features) can expose a
 *     second profile that contains a copy of a few system apps such as the Play Store. On TV only the
 *     current user's apps are shown; elsewhere an app that also exists in the current user is shown once.
 *  2. Same name, different packages. When two launchable packages carry exactly the same name and
 *     either come from the same developer (same signing certificate) or one is a tiny stub next to a
 *     real app, only the real app (largest installed APK) is kept.
 *
 * Nothing is ever added: the result is always a subset of what PackageManager / LauncherApps returned.
 */
object AppDedup {
    enum class Reason { OTHER_PROFILE, SAME_APP_OTHER_PROFILE, SAME_NAME_SAME_DEVELOPER, SAME_NAME_STUB }

    data class Removed(val entry: AppEntry, val keptKey: String?, val reason: Reason)
    data class Result(val apps: List<AppEntry>, val removed: List<Removed>)

    const val STUB_MAX_BYTES = 512L * 1024
    const val STUB_RATIO = 4

    fun normalizedLabel(label: String): String =
        label.lowercase(Locale.ROOT).replace("™", "").replace("®", "").trim().replace(Regex("\\s+"), " ")

    fun dedupe(
        entries: List<AppEntry>,
        myUserSerial: Long,
        isTv: Boolean,
        apkBytes: (AppEntry) -> Long,
        signer: (AppEntry) -> String?,
    ): Result {
        val removed = ArrayList<Removed>()
        // 1. profiles
        val mine = entries.filter { it.userSerial == myUserSerial }
        val myPackages = mine.mapTo(HashSet()) { it.packageName }
        val kept = ArrayList<AppEntry>(entries.size)
        for (e in entries) {
            when {
                e.userSerial == myUserSerial -> kept += e
                isTv -> removed += Removed(e, null, Reason.OTHER_PROFILE)
                e.packageName in myPackages -> removed += Removed(e, mine.first { it.packageName == e.packageName }.key, Reason.SAME_APP_OTHER_PROFILE)
                else -> kept += e
            }
        }
        // 2. same name inside one user
        val drop = HashSet<String>()
        kept.groupBy { it.userSerial to normalizedLabel(it.label) }.values.filter { g -> g.map { it.packageName }.distinct().size > 1 }.forEach { group ->
            val sizes = group.associateWith { apkBytes(it) }
            val ordered = group.sortedWith(compareByDescending<AppEntry> { sizes[it] ?: 0L }.thenByDescending { it.isLeanback == isTv }.thenBy { it.packageName })
            val keeper = ordered.first()
            val keeperSigner = signer(keeper)
            val keeperSize = sizes[keeper] ?: 0L
            for (other in ordered.drop(1)) {
                if (other.packageName == keeper.packageName) continue
                val otherSize = sizes[other] ?: 0L
                val sameDev = keeperSigner != null && keeperSigner == signer(other)
                val stub = otherSize in 1..STUB_MAX_BYTES && keeperSize >= otherSize * STUB_RATIO
                if (sameDev || stub) {
                    drop += other.key
                    removed += Removed(other, keeper.key, if (sameDev) Reason.SAME_NAME_SAME_DEVELOPER else Reason.SAME_NAME_STUB)
                }
            }
        }
        return Result(kept.filter { it.key !in drop }, removed)
    }
}
