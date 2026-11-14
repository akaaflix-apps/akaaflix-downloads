// Root build file. Shared Android configuration for every library module lives here so each
// feature module only declares its namespace and dependencies.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

object AkaaSdk {
    const val MIN = 23      // Android 6.0 Marshmallow
    const val COMPILE = 37  // Android 17 – newest stable platform installed
    const val TARGET = 37
}
extra["akaa.minSdk"] = AkaaSdk.MIN
extra["akaa.compileSdk"] = AkaaSdk.COMPILE
extra["akaa.targetSdk"] = AkaaSdk.TARGET

subprojects {
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            compileSdk = AkaaSdk.COMPILE
            defaultConfig {
                minSdk = AkaaSdk.MIN
                consumerProguardFiles("consumer-rules.pro")
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            buildFeatures {
                viewBinding = true
            }
            testOptions {
                unitTests.isIncludeAndroidResources = true
                unitTests.isReturnDefaultValues = true
            }
            lint {
                abortOnError = false
            }
        }
    }
}

// Optional: alternative Maven repository for Robolectric's android-all jars (e.g. a mirror).
// Set akaa.robolectric.repoUrl in ~/.gradle/gradle.properties when Maven Central is unreachable.
subprojects {
    tasks.withType<Test>().configureEach {
        (project.findProperty("akaa.robolectric.repoUrl") as String?)?.takeIf { it.isNotBlank() }?.let {
            systemProperty("robolectric.dependency.repo.url", it)
        }
        maxHeapSize = "3g"
    }
}
