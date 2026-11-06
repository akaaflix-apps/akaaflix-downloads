package com.akaaflix.prime.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.akaaflix.prime.AkaaFlixApp
import com.akaaflix.prime.MainActivity
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.TimeFormat
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh of the active playlist: re-checks account status, refreshes
 * the catalog and records local notifications for new content and upcoming expiry.
 */
class PlaylistRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AkaaFlixApp).container
        val settings = container.settingsRepository.current()
        val playlist = container.playlistRepository.observeActive().first() ?: return Result.success()

        when (val account = container.playlistRepository.refreshAccount(playlist.id)) {
            is AppResult.Success -> {
                val exp = account.data.expiresAtEpochSec
                val days = exp?.let { TimeFormat.remainingDays(it) }
                if (days != null && days <= EXPIRY_WARNING_DAYS) {
                    val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                    if (container.notificationRepository.countSince(TYPE_EXPIRY, playlist.id, since) == 0) {
                        val title = applicationContext.getString(R.string.notif_expiry_title)
                        val msg = applicationContext.getString(R.string.notif_expiry_message, playlist.name, days.toInt())
                        container.notificationRepository.add(playlist.id, TYPE_EXPIRY, title, msg)
                        if (settings.notificationsEnabled) postSystemNotification(applicationContext, title, msg, NOTIF_EXPIRY_ID)
                    }
                }
            }
            is AppResult.Failure -> return Result.retry()
        }

        when (val sync = container.catalogSyncer.sync(playlist.id)) {
            is AppResult.Success -> {
                val newMovies = sync.data.newMovies
                val newSeries = sync.data.newSeries
                if (newMovies + newSeries > 0) {
                    val title = applicationContext.getString(R.string.notif_new_content_title)
                    val msg = applicationContext.getString(R.string.notif_new_content_message, newMovies, newSeries)
                    container.notificationRepository.add(playlist.id, TYPE_NEW_CONTENT, title, msg)
                    if (settings.notificationsEnabled) postSystemNotification(applicationContext, title, msg, NOTIF_CONTENT_ID)
                }
            }
            is AppResult.Failure -> return Result.retry()
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "akaaflix_playlist_refresh"
        const val TYPE_EXPIRY = "EXPIRY"
        const val TYPE_NEW_CONTENT = "NEW_CONTENT"
        const val CHANNEL_ID = "akaaflix_updates"
        private const val NOTIF_EXPIRY_ID = 1001
        private const val NOTIF_CONTENT_ID = 1002
        private const val EXPIRY_WARNING_DAYS = 7L

        /** Schedules (or cancels when [hours] = 0) the periodic refresh. */
        fun schedule(context: Context, hours: Int) {
            val wm = WorkManager.getInstance(context)
            if (hours <= 0) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }

        fun postSystemNotification(context: Context, title: String, message: String, id: Int) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(context)
            val intent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(0xFFF01835.toInt())
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, n) }
        }
    }
}
