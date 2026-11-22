package app.pulsify.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class IntroShotTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun pages() {
        rule.mainClock.autoAdvance = false
        rule.setContent { IntroScreen { } }
        for (i in 0 until 5) {
            rule.mainClock.advanceTimeBy(2600)
            rule.waitForIdle()
            val v = rule.activity.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(android.graphics.Canvas(b))
            FileOutputStream("/tmp/intro_$i.png").use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (i < 4) { rule.onNodeWithText("Next").performClick(); rule.mainClock.advanceTimeBy(1200) }
        }
    }

    @Test fun legal() {
        rule.setContent { PulsifyTheme(androidx.compose.ui.graphics.Color(0xFFCDB4FF), androidx.compose.ui.graphics.Color(0xFFFFB36B)) { LegalPage(LegalDoc.PRIVACY) { } } }
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(android.graphics.Canvas(b))
        FileOutputStream("/tmp/legal.png").use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
