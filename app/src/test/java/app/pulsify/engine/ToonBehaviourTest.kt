package app.pulsify.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.pulsify.core.AudioFrame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ToonBehaviourTest {
    private fun snap(s: ToonScene, name: String) {
        val b = Bitmap.createBitmap(500, 520, Bitmap.Config.ARGB_8888); val cv = Canvas(b); cv.drawColor(Color.rgb(16, 20, 32))
        cv.translate(250f, 440f); cv.scale(130f, 130f); s.debugDraw(cv, 130f)
        FileOutputStream("/tmp/tb_$name.png").use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun run(s: ToonScene, inp: SceneInput, secs: Float) { var t = 0f; while (t < secs) { s.update(1 / 60f, inp); t += 1 / 60f } }

    @Test fun throwFallGetUp() {
        for ((name, kind) in listOf("pup" to PupKind, "duck" to DuckKind, "bird" to BirdKind, "sq" to SquirrelKind)) {
            val s = ToonScene(kind) { it }; s.onSurfaceChanged(1080, 2340)
            val inp = SceneInput()
            run(s, inp, 1f)
            // grab the body and fling it up and to the right
            s.touchDown(0.5f, 0.8f); run(s, inp, .05f)
            var y = .8f; var x = .5f
            for (i in 0 until 12) { x += .02f; y -= .03f; s.touchMove(x, y); run(s, inp, 1 / 60f) }
            snap(s, "${name}_carried")
            s.touchUp(); run(s, inp, .25f); snap(s, "${name}_air")
            run(s, inp, 1.4f); snap(s, "${name}_land")
            println("$name after throw: ${s.debugState}")
            run(s, inp, 3f); snap(s, "${name}_up"); println("$name later: ${s.debugState}")
            // every trick, music on
            inp.audio.source = AudioFrame.Source.MUSIC; inp.audio.beat = 1f
            for (t in ToonScene.TRICKS.keys) { s.perform(t); run(s, inp, .5f) }
            run(s, inp, 6f); snap(s, "${name}_dance")
            inp.audio.source = AudioFrame.Source.NONE; inp.charging = true; run(s, inp, 2f); snap(s, "${name}_charge")
        }
    }
}
