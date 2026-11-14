plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.akaaflix.launcher.apps"
}

dependencies {
    api(project(":launcher-core"))
    api(project(":user-preferences"))
    api(project(":remote-navigation"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
