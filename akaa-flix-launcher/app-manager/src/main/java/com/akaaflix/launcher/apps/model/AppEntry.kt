package com.akaaflix.launcher.apps.model

import android.content.ComponentName

enum class AppCategory(val id: String) {
    ENTERTAINMENT("entertainment"), TOOLS("tools"), OTHER("other");
    companion object { fun of(id: String?): AppCategory? = entries.firstOrNull { it.id == id } }
}

/**
 * How the app is installed, which decides what Android lets a normal launcher do with it:
 *  - [USER]: installed by the user -> can be uninstalled (system confirmation dialog).
 *  - [UPDATED_SYSTEM]: pre-installed, updated from a store -> updates can be removed / app disabled in Android settings.
 *  - [SYSTEM]: pre-installed -> can only be disabled in Android settings, when the device allows it.
 *  - [PROTECTED]: core / persistent system app -> cannot be disabled or removed without system or Device Owner privileges.
 */
enum class AppInstallType(val id: String) {
    USER("user"), UPDATED_SYSTEM("updated_system"), SYSTEM("system"), PROTECTED("protected");
    val canUninstall: Boolean get() = this == USER
    companion object { fun of(id: String?): AppInstallType = entries.firstOrNull { it.id == id } ?: USER }
}

/** A launchable activity of an installed app (real data from PackageManager / LauncherApps). */
data class AppEntry(
    val key: String,
    val packageName: String,
    val className: String,
    val userSerial: Long,
    val label: String,
    val isLeanback: Boolean,
    val installTime: Long,
    val updateTime: Long,
    val versionCode: Long,
    val autoCategory: AppCategory,
    val isWorkProfile: Boolean,
    val installType: AppInstallType = AppInstallType.USER,
) {
    val component: ComponentName get() = ComponentName(packageName, className)

    companion object {
        fun key(pkg: String, cls: String, userSerial: Long) = "$pkg/$cls#$userSerial"
        fun packageOfKey(key: String): String = key.substringBefore('/')
    }
}

enum class AppFilter { ALL, ENTERTAINMENT, TOOLS }
