# ---- Akaa Flix Prime R8 rules ----

# Keep line numbers for readable crash reports, hide original source file names.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# kotlinx.serialization: keep generated serializers for our DTOs and backup models.
-keepclassmembers @kotlinx.serialization.Serializable class com.akaaflix.prime.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.akaaflix.prime.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.akaaflix.prime.**$$serializer { *; }
-keep class com.akaaflix.prime.network.dto.** { *; }
-keep class com.akaaflix.prime.data.backup.** { *; }

# Retrofit service interfaces are created reflectively.
-keep,allowobfuscation interface com.akaaflix.prime.network.XtreamApi
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room entities/DAOs are generated; keep entity classes for safety.
-keep class com.akaaflix.prime.database.entity.** { *; }

# Media3 RTMP extension is loaded reflectively by DefaultDataSource.
-keep class androidx.media3.datasource.rtmp.RtmpDataSource { *; }
-keep class net.butterflytv.rtmp_client.** { *; }

# WorkManager workers are instantiated by class name.
-keep class com.akaaflix.prime.work.** extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
