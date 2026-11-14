package com.akaaflix.launcher.device.weather

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Periodic weather refresh through the platform JobScheduler (no WorkManager dependency, no
 * long-running service): runs about once an hour, only with a network connection, and only while
 * the weather widget is enabled with a city. It refreshes the stored city's forecast; it never
 * reads the device location.
 */
class WeatherRefreshJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            val ok = try { WeatherRepository.get(applicationContext).refreshIfStale() } catch (_: Exception) { false }
            jobFinished(params, !ok)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean { running?.cancel(); return true }

    override fun onDestroy() { running?.cancel(); super.onDestroy() }

    companion object {
        const val JOB_ID = 0xAF1
        const val PERIOD_MS = 60 * 60 * 1000L

        /** Schedules or cancels the job to match the settings. Safe to call often (no-op when unchanged). */
        fun sync(context: Context, enabled: Boolean) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val pending = try { js.allPendingJobs.any { it.id == JOB_ID } } catch (_: Exception) { false }
            if (!enabled) { if (pending) js.cancel(JOB_ID); return }
            if (pending) return
            val b = JobInfo.Builder(JOB_ID, ComponentName(context, WeatherRefreshJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
            if (Build.VERSION.SDK_INT >= 24) b.setPeriodic(PERIOD_MS, 15 * 60 * 1000L) else b.setPeriodic(PERIOD_MS)
            if (Build.VERSION.SDK_INT >= 26) b.setRequiresBatteryNotLow(true)
            try { js.schedule(b.build()) } catch (_: Exception) { }
        }
    }
}
