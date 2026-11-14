package com.akaaflix.launcher.apps.model

import android.content.ComponentName

enum class AppCategory(val id: String) {
    ENTERTAINMENT("entertainment"), TOOLS("tools"), OTHER("other");
    companion object { fun of(id: String?): AppCategory? = entries.firstOrNull { it.id == id } }
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
) {
    val component: ComponentName get() = ComponentName(packageName, className)

    companion object {
        fun key(pkg: String, cls: String, userSerial: Long) = "$pkg/$cls#$userSerial"
        fun packageOfKey(key: String): String = key.substringBefore('/')
    }
}

enum class AppFilter { ALL, ENTERTAINMENT, TOOLS }
