package com.akaaflix.launcher

import android.app.Application
import android.os.StrictMode
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.apps.icons.IconCache
import com.akaaflix.launcher.core.AppConfig
import com.akaaflix.launcher.prefs.SettingsRepository
import com.akaaflix.launcher.wallpaper.ThumbnailLoader

class AkaaLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppConfig.versionName = BuildConfig.VERSION_NAME
        AppConfig.versionCode = BuildConfig.VERSION_CODE.toLong()
        AppConfig.isDebug = BuildConfig.DEBUG
        AppConfig.updateRepo = BuildConfig.UPDATE_REPO
        AppConfig.updateTagPrefix = BuildConfig.UPDATE_TAG_PREFIX
        AppConfig.weatherApiKey = BuildConfig.WEATHER_API_KEY
        AppConfig.applicationId = BuildConfig.APPLICATION_ID

        CrashGuard.install(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().detectCustomSlowCalls().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build())
        }
        // Settings load starts here (async); app discovery starts immediately from the cache.
        val prefs = SettingsRepository.get(this)
        AppRepository.get(this).start()
        if (CrashGuard.shouldEnterSafeMode(this)) {
            prefs.edit { it.copy(safeMode = true) }
            CrashGuard.reset(this)
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        IconCache.get(this).trim(level)
        if (level >= TRIM_MEMORY_MODERATE) ThumbnailLoader.get(this).clear()
    }
}
