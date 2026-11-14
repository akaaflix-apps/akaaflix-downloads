package com.akaaflix.launcher.apps.privileged

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.akaaflix.launcher.core.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Optional advanced access through the user's own Shizuku service (official Shizuku API and
 * permission model). Akaa Flix never has elevated rights by itself: commands run only while Shizuku
 * is running (started by the user via wireless debugging / ADB, or root/Sui) and the user granted
 * Akaa Flix access in Shizuku's own permission dialog.
 *
 * The Shizuku API library needs Android 7.0+; on Android 6 the launcher offers the ADB commands instead.
 */
object Privileged {
    enum class State { UNSUPPORTED_ANDROID, NOT_INSTALLED, NOT_RUNNING, PERMISSION_REQUIRED, PERMISSION_DENIED, READY }

    data class Status(val state: State, val uid: Int = -1) {
        val ready get() = state == State.READY
        val isRoot get() = uid == 0
    }

    data class Result(val ok: Boolean, val code: Int, val output: String)

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val MIN_SDK = 24
    private const val REQUEST_CODE = 0xAF11

    fun status(context: Context): Status {
        if (Build.VERSION.SDK_INT < MIN_SDK) return Status(State.UNSUPPORTED_ANDROID)
        return Impl.status(context.applicationContext)
    }

    private val _state = MutableStateFlow(Status(State.NOT_INSTALLED))
    /** Live connection state: updated when Shizuku starts, stops (binder death) or the permission changes. */
    val state: StateFlow<Status> = _state.asStateFlow()
    @Volatile private var watching = false

    /** Starts following Shizuku's connection (idempotent) and publishes the current state. */
    fun watch(context: Context): Status {
        val c = context.applicationContext
        if (Build.VERSION.SDK_INT >= MIN_SDK && !watching) {
            watching = true
            Impl.watch { refresh(c) }
        }
        return refresh(c)
    }

    /** Re-reads the state from Shizuku ("Check again"). */
    fun refresh(context: Context): Status = status(context.applicationContext).also { _state.value = it }

    /** Shows Shizuku's permission dialog (handled by the Shizuku app). */
    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < MIN_SDK) { onResult(false); return }
        Impl.requestPermission(onResult)
    }

    suspend fun exec(context: Context, argv: List<String>): Result {
        if (Build.VERSION.SDK_INT < MIN_SDK) return Result(false, -1, "unsupported")
        return Impl.exec(context.applicationContext, argv)
    }

    fun release() { if (Build.VERSION.SDK_INT >= MIN_SDK) Impl.release() }

    /** All Shizuku classes are referenced only from here, after the API-level check. */
    private object Impl {
        @Volatile private var service: IShellService? = null
        private var connection: ServiceConnection? = null
        private var args: rikka.shizuku.Shizuku.UserServiceArgs? = null

        fun status(c: Context): Status {
            val binderAlive = try { rikka.shizuku.Shizuku.pingBinder() } catch (_: Throwable) { false }
            if (!binderAlive) {
                val installed = try { c.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0); true } catch (_: PackageManager.NameNotFoundException) { false } catch (_: Exception) { false }
                return Status(if (installed) State.NOT_RUNNING else State.NOT_INSTALLED)
            }
            return try {
                if (rikka.shizuku.Shizuku.isPreV11()) return Status(State.NOT_RUNNING)
                val uid = rikka.shizuku.Shizuku.getUid()
                when {
                    rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status(State.READY, uid)
                    rikka.shizuku.Shizuku.shouldShowRequestPermissionRationale() -> Status(State.PERMISSION_DENIED, uid)
                    else -> Status(State.PERMISSION_REQUIRED, uid)
                }
            } catch (_: Throwable) { Status(State.NOT_RUNNING) }
        }

        /** Shizuku delivers these on the main thread. A dead binder also kills our user service. */
        fun watch(onChange: () -> Unit) {
            try {
                rikka.shizuku.Shizuku.addBinderReceivedListenerSticky { onChange() }
                rikka.shizuku.Shizuku.addBinderDeadListener { service = null; connection = null; args = null; onChange() }
                rikka.shizuku.Shizuku.addRequestPermissionResultListener { _, _ -> onChange() }
            } catch (_: Throwable) { }
        }

        fun requestPermission(onResult: (Boolean) -> Unit) {
            try {
                if (rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { onResult(true); return }
                val listener = object : rikka.shizuku.Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                        if (requestCode != REQUEST_CODE) return
                        rikka.shizuku.Shizuku.removeRequestPermissionResultListener(this)
                        onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
                rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener)
                rikka.shizuku.Shizuku.requestPermission(REQUEST_CODE)
            } catch (_: Throwable) { onResult(false) }
        }

        private suspend fun bind(c: Context): IShellService? {
            service?.let { if (it.asBinder().isBinderAlive) return it }
            return withContext(Dispatchers.Main) {
                withTimeoutOrNull(15_000) {
                    suspendCancellableCoroutine<IShellService?> { cont ->
                        val a = rikka.shizuku.Shizuku.UserServiceArgs(ComponentName(c.packageName, ShellService::class.java.name))
                            .daemon(false).processNameSuffix("akaa_shell").debuggable(AppConfig.isDebug).version(AppConfig.versionCode.toInt())
                        val conn = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                                val s = if (binder != null && binder.pingBinder()) IShellService.Stub.asInterface(binder) else null
                                // The shell process can die independently (Shizuku stopped, killed): rebind on next use.
                                try { binder?.linkToDeath({ service = null }, 0) } catch (_: Exception) { }
                                service = s
                                if (cont.isActive) cont.resume(s)
                            }
                            override fun onServiceDisconnected(name: ComponentName?) { service = null }
                        }
                        args = a; connection = conn
                        try { rikka.shizuku.Shizuku.bindUserService(a, conn) } catch (_: Throwable) { if (cont.isActive) cont.resume(null) }
                    }
                }
            }
        }

        suspend fun exec(c: Context, argv: List<String>): Result {
            if (status(c).state != State.READY) return Result(false, -1, "shizuku not ready")
            val s = bind(c) ?: return Result(false, -1, "service not available")
            return withContext(Dispatchers.IO) {
                try {
                    val raw = s.exec(argv.toTypedArray()) ?: "-1\n"
                    val code = raw.substringBefore('\n').trim().toIntOrNull() ?: -1
                    val out = raw.substringAfter('\n', "").trim()
                    // pm prints "Success" / "Failure [..]"; a non-zero exit or "Failure"/"Error" is a failure.
                    val ok = code == 0 && !out.contains("Failure") && !out.startsWith("Error") && !out.contains("Exception")
                    Result(ok, code, out)
                } catch (e: Exception) { Result(false, -1, e.javaClass.simpleName) }
            }
        }

        fun release() {
            val a = args; val conn = connection
            if (a != null && conn != null) try { rikka.shizuku.Shizuku.unbindUserService(a, conn, true) } catch (_: Throwable) { }
            service = null; connection = null; args = null
        }
    }
}
