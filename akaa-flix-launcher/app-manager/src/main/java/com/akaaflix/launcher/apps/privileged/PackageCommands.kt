package com.akaaflix.launcher.apps.privileged

/**
 * Exact commands used for advanced package management (pure, unit tested). The same strings are
 * shown to the user as ADB instructions when Shizuku is not available, so both paths do the same.
 */
object PackageCommands {
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val COMPONENT = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$")

    fun isValidPackage(p: String) = PACKAGE.matches(p)

    private fun pkg(p: String): String { require(isValidPackage(p)) { "invalid package name" }; return p }

    fun disable(p: String, user: Int) = listOf("pm", "disable-user", "--user", user.toString(), pkg(p))
    fun enable(p: String, user: Int) = listOf("pm", "enable", "--user", user.toString(), pkg(p))
    /** Removes the package for this user only; the APK stays on the system partition (restorable). */
    fun removeForUser(p: String, user: Int) = listOf("pm", "uninstall", "-k", "--user", user.toString(), pkg(p))
    fun restore(p: String, user: Int) = listOf("pm", "install-existing", "--user", user.toString(), pkg(p))
    /** Android 10+: back to the factory version of a pre-installed app. */
    fun uninstallUpdates(p: String) = listOf("pm", "uninstall-system-updates", pkg(p))
    /** Android 10+: makes [p] the holder of the HOME role (the default launcher). */
    fun setHomeRole(p: String, user: Int) = listOf("cmd", "role", "add-role-holder", "--user", user.toString(), "android.app.role.HOME", pkg(p))
    /** Android 7–9 fallback: preferred HOME activity. */
    fun setHomeActivity(component: String, user: Int): List<String> {
        require(COMPONENT.matches(component)) { "invalid component" }
        return listOf("cmd", "package", "set-home-activity", "--user", user.toString(), component)
    }

    /** Text for the ADB fallback dialog. */
    fun asAdb(cmd: List<String>) = "adb shell " + cmd.joinToString(" ")

    /** Current Android user id (works on every API level). */
    fun currentUser(): Int = android.os.Process.myUid() / 100_000
}
