plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.akaaflix.launcher.customization"
}

dependencies {
    implementation(project(":home-screen"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
