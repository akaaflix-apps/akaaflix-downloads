plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.akaaflix.launcher.home"
}

dependencies {
    api(project(":launcher-core"))
    api(project(":user-preferences"))
    api(project(":remote-navigation"))
    api(project(":device-information"))
    api(project(":app-manager"))
    api(project(":wallpaper-engine"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
