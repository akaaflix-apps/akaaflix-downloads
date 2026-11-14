plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.akaaflix.launcher.prefs"
}

dependencies {
    api(project(":launcher-core"))
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
