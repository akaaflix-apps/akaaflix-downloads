plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.akaaflix.launcher.apps"
    buildFeatures { aidl = true }
}

dependencies {
    api(project(":launcher-core"))
    api(project(":user-preferences"))
    api(project(":remote-navigation"))
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
