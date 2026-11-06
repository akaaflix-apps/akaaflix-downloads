package com.akaaflix.prime

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.work.Configuration
import android.util.Log
import com.akaaflix.prime.di.AppContainer
import com.akaaflix.prime.work.PlaylistRefreshWorker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

open class AkaaFlixApp : Application(), ImageLoaderFactory, Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        PlaylistRefreshWorker.ensureChannel(this)
        // Keep the periodic refresh in sync with the "Auto refresh" setting.
        container.appScope.launch {
            container.settingsRepository.settings
                .map { it.autoRefreshHours }
                .distinctUntilChanged()
                .collect { hours ->
                    runCatching { PlaylistRefreshWorker.schedule(this@AkaaFlixApp, hours) }
                        .onFailure { Log.w("AkaaFlixApp", "Could not schedule background refresh", it) }
                }
        }
    }

    /** Dependency container; tests may override this (e.g. to replace the Keystore cipher). */
    protected open fun createContainer(): AppContainer = AppContainer(this)

    /** On-demand WorkManager initialisation (works even when androidx.startup has not run). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()

    /** Coil image loader: posters are cached on disk and in memory, never preloaded in bulk. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { container.okHttpClient }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(IMAGE_CACHE_BYTES).build() }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()

    companion object {
        const val IMAGE_CACHE_BYTES = 250L * 1024 * 1024
    }
}
