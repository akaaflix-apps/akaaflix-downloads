package com.akaaflix.prime

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.akaaflix.prime.common.DeviceDetector
import com.akaaflix.prime.data.settings.LocaleMirror
import com.akaaflix.prime.navigation.AkaaApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** Applies the in-app language (Arabic RTL / English LTR) before any resources are read. */
    override fun attachBaseContext(newBase: Context) {
        val tag = LocaleMirror.read(newBase)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AkaaFlixApp).container
        val detected = DeviceDetector.detect(this)

        // Recreate the activity when the language changes so every string and the layout direction update.
        lifecycleScope.launch {
            container.settingsRepository.settings
                .map { it.language.tag }
                .distinctUntilChanged()
                .drop(1)
                .collect { tag ->
                    if (tag != resources.configuration.locales[0].language) recreate()
                }
        }

        setContent { AkaaApp(container = container, detectedDevice = detected) }
    }
}
