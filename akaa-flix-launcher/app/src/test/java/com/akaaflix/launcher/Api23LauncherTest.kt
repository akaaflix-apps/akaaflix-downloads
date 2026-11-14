package com.akaaflix.launcher

import org.robolectric.annotation.Config

/**
 * Same end-to-end flows on Android 6.0 (API 23). Robolectric 4.18 no longer ships API 23, so this
 * class only runs with an older Robolectric:
 *   ./gradlew :app:testDebugUnitTest -Pakaa.robolectricVersion=4.14.1 --tests "*Api23*"
 */
@Config(sdk = [23], application = AkaaLauncherApp::class, qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
class Api23LauncherTest : LauncherIntegrationTest() {
    override val fontFamily = "system"
}
