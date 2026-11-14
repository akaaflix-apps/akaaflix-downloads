# Shizuku starts the user service by class name in its own process.
-keep class com.akaaflix.launcher.apps.privileged.ShellService { <init>(...); *; }
-keep class com.akaaflix.launcher.apps.privileged.IShellService { *; }
-keep class com.akaaflix.launcher.apps.privileged.IShellService$Stub { *; }
