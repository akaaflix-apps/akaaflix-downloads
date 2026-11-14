import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

fun prop(name: String): String? =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.akaaflix.launcher"
    compileSdk = rootProject.extra["akaa.compileSdk"] as Int

    defaultConfig {
        applicationId = "com.akaaflix.launcher"
        minSdk = rootProject.extra["akaa.minSdk"] as Int
        targetSdk = rootProject.extra["akaa.targetSdk"] as Int
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "UPDATE_REPO", "\"${prop("akaa.update.repo") ?: ""}\"")
        buildConfigField("String", "UPDATE_TAG_PREFIX", "\"${prop("akaa.update.tagPrefix") ?: "launcher-v"}\"")
        buildConfigField("String", "WEATHER_API_KEY", "\"${prop("akaa.weather.apiKey") ?: ""}\"")
    }

    val keystorePath = prop("AKAAFLIX_LAUNCHER_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = prop("AKAAFLIX_LAUNCHER_KEYSTORE_PASSWORD")
                keyAlias = prop("AKAAFLIX_LAUNCHER_KEY_ALIAS")
                keyPassword = prop("AKAAFLIX_LAUNCHER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    androidResources {
        localeFilters += listOf("en", "ar")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.maxHeapSize = "3g"
            // API 23 tests need an older Robolectric (see Api23LauncherTest).
            if (project.findProperty("akaa.robolectricVersion") == null) it.filter.excludeTestsMatching("*Api23*")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += setOf("META-INF/*.version", "META-INF/**/LICENSE*", "kotlin/**", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation(project(":launcher-core"))
    implementation(project(":remote-navigation"))
    implementation(project(":user-preferences"))
    implementation(project(":device-information"))
    implementation(project(":app-manager"))
    implementation(project(":wallpaper-engine"))
    implementation(project(":home-screen"))
    implementation(project(":customization"))
    implementation(project(":settings"))

    testImplementation(libs.junit)
    // -Pakaa.robolectricVersion=4.14.1 runs the suite with an older Robolectric that still ships Android 6.0 (API 23).
    val robolectricOverride = project.findProperty("akaa.robolectricVersion") as String?
    if (robolectricOverride != null) testImplementation("org.robolectric:robolectric:$robolectricOverride") else testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
