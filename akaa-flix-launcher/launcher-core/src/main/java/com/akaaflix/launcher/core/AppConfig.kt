package com.akaaflix.launcher.core

/**
 * Build-time values that library modules need but cannot read from the app module's BuildConfig.
 * Initialised once by the Application before anything else runs.
 */
object AppConfig {
    @Volatile var versionName: String = "1.0.0"
    @Volatile var versionCode: Long = 1
    @Volatile var isDebug: Boolean = false
    @Volatile var updateRepo: String = ""
    @Volatile var updateTagPrefix: String = "launcher-v"
    @Volatile var weatherApiKey: String = ""
    @Volatile var applicationId: String = "com.akaaflix.launcher"
}
