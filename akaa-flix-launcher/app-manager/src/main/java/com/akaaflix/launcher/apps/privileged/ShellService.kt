package com.akaaflix.launcher.apps.privileged

import androidx.annotation.Keep
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Shizuku "user service": Shizuku starts this class in its own process with the shell (adb) or root
 * identity, only after the user granted Akaa Flix permission in the Shizuku app. It runs the fixed
 * package-manager commands built by [PackageCommands] – argv arrays, never a shell string, so a
 * package name can never inject another command.
 */
@Keep
class ShellService : IShellService.Stub() {

    override fun destroy() { exitProcess(0) }

    override fun uid(): Int = android.os.Process.myUid()

    override fun exec(argv: Array<out String>?): String {
        val cmd = argv?.toList().orEmpty()
        if (cmd.isEmpty() || cmd[0] !in ALLOWED_BINARIES) return "-1\nrejected"
        return try {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = StringBuilder()
            val reader = Thread { p.inputStream.bufferedReader().use { r -> r.lineSequence().take(400).forEach { out.append(it).append('\n') } } }
            reader.start()
            val finished = if (android.os.Build.VERSION.SDK_INT >= 26) p.waitFor(60, TimeUnit.SECONDS) else { p.waitFor(); true }
            if (!finished) { p.destroy(); return "-2\ntimeout" }
            reader.join(2000)
            "${p.exitValue()}\n${out.toString().trim()}"
        } catch (e: Exception) {
            "-1\n${e.javaClass.simpleName}: ${e.message}"
        }
    }

    companion object {
        /** Only the package manager / role tools are allowed. */
        val ALLOWED_BINARIES = setOf("pm", "cmd")
    }
}
