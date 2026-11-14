# Akaa Flix Launcher – R8 rules.
# The app uses no reflection-based serialization; only Android framework entry points are kept
# (Activities, Services, Receivers are kept automatically through the merged manifest).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# NotificationListenerService subclass is referenced from the manifest (kept automatically).
