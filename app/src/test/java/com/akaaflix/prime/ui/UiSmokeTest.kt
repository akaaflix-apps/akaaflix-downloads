package com.akaaflix.prime.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akaaflix.prime.AkaaFlixApp
import com.akaaflix.prime.MainActivity
import com.akaaflix.prime.R
import com.akaaflix.prime.di.AppContainer
import com.akaaflix.prime.testutil.FakeCipher
import com.akaaflix.prime.testutil.FakeXtreamServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import java.io.File

/** Test application: identical to the real one except for the Keystore-free cipher. */
class SmokeTestApp : AkaaFlixApp() {
    override fun createContainer(): AppContainer = AppContainer(this, FakeCipher())
}

/**
 * End-to-end UI smoke test against the MOCK Xtream server: first-run Add Playlist, catalog
 * sync, Home, Live TV, Movies, Series, details, Settings and Account render without crashing.
 * Screenshots are written to app/build/screenshots for visual review.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class UiSmokeBase(private val prefix: String) {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    protected lateinit var fake: FakeXtreamServer
    private lateinit var base: String

    @Before fun startServer() { fake = FakeXtreamServer(); base = fake.start() }
    @After fun stopServer() = fake.shutdown()

    protected fun str(id: Int) = rule.activity.getString(id)

    protected fun shot(name: String) {
        rule.waitForIdle()
        val dir = File("build/screenshots").apply { mkdirs() }
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(dir, "$prefix-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Polls for [text] while advancing Robolectric's virtual main-looper clock (coroutine delays, animations). */
    protected fun waitForText(text: String, timeoutMs: Long = 20_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) return
            ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
            Thread.sleep(25) // let OkHttp / Room background threads progress
        }
        shot("timeout")
        throw AssertionError("Timed out waiting for \"$text\"")
    }

    protected fun SemanticsNodeInteractionCollection.firstClick() = this[0].performClick()

    protected fun addPlaylistAndReachHome() {
        waitForText(str(R.string.playlist_add_title))
        shot("01-add-playlist")
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Demo Playlist")
        fields[1].performTextInput("demo_user")
        fields[2].performTextInput("s3cret!")
        fields[3].performTextInput(base)
        rule.onAllNodesWithText(str(R.string.action_add_playlist)).firstClick()
        waitForText(str(R.string.nav_home), 30_000)
        waitForText("Kingdom Tales") // hero content from the synced catalog
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS) // let the navigation transition finish
        shot("02-home")
    }

    protected fun open(labelRes: Int) {
        rule.onAllNodesWithText(str(labelRes)).firstClick()
        rule.waitForIdle()
    }
}

@Config(sdk = [34], application = SmokeTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneUiSmokeTest : UiSmokeBase("phone") {
    @Test fun firstRun_sync_andMainScreens() {
        addPlaylistAndReachHome()
        open(R.string.nav_live); waitForText("Al News HD"); shot("03-live")
        open(R.string.nav_movies); waitForText("Quiet Harbor"); shot("04-movies")
        rule.onAllNodesWithText("Quiet Harbor").firstClick()
        waitForText(str(R.string.action_watch_now)); shot("05-movie-details")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        open(R.string.nav_series); waitForText("Kingdom Tales"); shot("06-series")
        rule.onAllNodesWithText("Kingdom Tales").firstClick()
        waitForText("Pilot"); shot("07-series-details")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        open(R.string.nav_my_list); shot("08-my-list")
    }
}

@Config(sdk = [34], application = SmokeTestApp::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvUiSmokeTest : UiSmokeBase("tv") {
    @Test fun firstRun_sync_andMainScreens() {
        addPlaylistAndReachHome()
        open(R.string.nav_live); waitForText("Al News HD"); shot("03-live")
        open(R.string.nav_movies); waitForText("Quiet Harbor"); shot("04-movies")
        open(R.string.nav_series); waitForText("Kingdom Tales"); shot("05-series")
        rule.onAllNodesWithText("Kingdom Tales").firstClick()
        waitForText("Pilot"); shot("06-series-details")
    }
}
