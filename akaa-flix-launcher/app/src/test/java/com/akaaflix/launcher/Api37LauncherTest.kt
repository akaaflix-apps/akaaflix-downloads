package com.akaaflix.launcher

import org.robolectric.annotation.Config

/** Android 17 (API 37) – the compile/target SDK – when the Robolectric version provides it. */
@Config(sdk = [37], application = AkaaLauncherApp::class, qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
class Api37LauncherTest : LauncherIntegrationTest()
