package app.pulsify.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.FileOutputStream

/** Renders each 2D creature to a PNG (real Skia via Robolectric native graphics) to check the port. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ToonRenderTest {
    private fun render(name: String, kind: ToonKind, coat: Int, setup: (Rig) -> Unit = {}) {
        val b = Bitmap.createBitmap(600, 760, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b); cv.drawColor(Color.rgb(16, 20, 32))
        val ink = ToonInk(); ink.begin(cv); ink.ups = 200f
        cv.translate(300f, 640f); cv.scale(200f, 200f)
        if (kind.perch) BirdKind.drawPerch(ink)
        ink.shadow(0f, .02f, .55f, 1f)
        val r = Rig(); r.base(); r.t = 1f; r.blink = 1f; setup(r)
        kind.draw(ink, r, coat)
        FileOutputStream("/tmp/toon_$name.png").use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun renderAll() {
        render("pup", PupKind, 0); render("pup_cream", PupKind, 1) { it.tongue = 1f; it.mouth = .6f; it.lx = .6f }
        render("duck", DuckKind, 0); render("duck_quack", DuckKind, 1) { it.mouth = 1f; it.wing = .8f }
        render("bird", BirdKind, 0); render("bird_budgie", BirdKind, 1); render("bird_fly", BirdKind, 2) { it.wing = 1f; it.flapPh = 1f }
        render("squirrel", SquirrelKind, 0); render("squirrel_grey", SquirrelKind, 1) { it.happy = 1f }
    }
}
