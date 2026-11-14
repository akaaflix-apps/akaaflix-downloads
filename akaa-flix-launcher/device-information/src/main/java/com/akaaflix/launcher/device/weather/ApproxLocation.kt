package com.akaaflix.launcher.device.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot approximate location for the weather city. Uses only ACCESS_COARSE_LOCATION, the
 * platform LocationManager (no Google Play services dependency) and never registers a continuous
 * listener: it reads a recent last-known fix or asks for a single fix with a timeout.
 */
object ApproxLocation {
    sealed class Result {
        data class Ok(val latitude: Double, val longitude: Double) : Result()
        data object NoPermission : Result()
        /** Location is switched off or the device has no network/fused location provider (common on TV boxes). */
        data object NoProvider : Result()
        data object Timeout : Result()
    }

    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    fun hasPermission(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, PERMISSION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun get(context: Context, maxAgeMs: Long = 6 * 3600_000L, timeoutMs: Long = 20_000L): Result {
        val c = context.applicationContext
        if (!hasPermission(c)) return Result.NoPermission
        val lm = c.getSystemService(LocationManager::class.java) ?: return Result.NoProvider
        val enabled = try { lm.getProviders(true) } catch (_: SecurityException) { return Result.NoPermission }
        val last = withContext(Dispatchers.IO) { bestLastKnown(lm, enabled) }
        val now = System.currentTimeMillis()
        if (last != null && now - last.time in 0..maxAgeMs) return Result.Ok(last.latitude, last.longitude)

        val provider = when {
            LocationManager.NETWORK_PROVIDER in enabled -> LocationManager.NETWORK_PROVIDER
            Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in enabled -> LocationManager.FUSED_PROVIDER
            // From Android 12 a coarse-only app may use any provider (results are coarsened by the system).
            Build.VERSION.SDK_INT >= 31 && LocationManager.GPS_PROVIDER in enabled -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) return last?.let { Result.Ok(it.latitude, it.longitude) } ?: Result.NoProvider
        val fresh = withTimeoutOrNull(timeoutMs) { single(c, lm, provider) }
        return when {
            fresh != null -> Result.Ok(fresh.latitude, fresh.longitude)
            last != null -> Result.Ok(last.latitude, last.longitude)
            else -> Result.Timeout
        }
    }

    private fun bestLastKnown(lm: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } }.maxByOrNull { it.time }

    private suspend fun single(c: Context, lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(c)) { loc ->
                if (cont.isActive) cont.resume(loc)
            }
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

}
