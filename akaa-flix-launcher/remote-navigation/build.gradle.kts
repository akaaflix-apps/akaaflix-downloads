plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.akaaflix.launcher.remote"
}

dependencies {
    api(project(":launcher-core"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
