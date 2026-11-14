// Runs inside the Shizuku user-service process (shell or root uid) – started only after the user
// granted Akaa Flix access in the Shizuku app.
package com.akaaflix.launcher.apps.privileged;

interface IShellService {
    /** Shizuku calls this transaction code when the service is unbound with remove = true. */
    void destroy() = 16777114;

    /** Runs a fixed command (argv, no shell interpretation). Returns "exitCode\noutput". */
    String exec(in String[] argv) = 1;

    /** uid of the service process (2000 = adb shell, 0 = root). */
    int uid() = 2;
}
