package app.pulsify.engine

import app.pulsify.core.AudioFrame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatueFlowTest {
    @Test fun flow() {
        val s = StatueScene(RuntimeEnvironment.getApplication()); s.onSurfaceChanged(1080, 2340)
        val inp = SceneInput(); inp.pagesKnown = true; inp.pagePos = 0f
        fun run(t: Float) { var k = 0f; while (k < t) { s.update(1 / 60f, inp); k += 1 / 60f } }
        fun cam(tag: String) { val f = StatueScene::class.java.declaredFields.associateBy { it.name }; fun g(n: String) = f[n]!!.apply { isAccessible = true }.getFloat(s)
            println("%-12s az=%7.1f el=%5.1f d=%5.2f y=%5.2f roll=%5.1f".format(tag, g("az"), g("el"), g("dist"), g("ty"), g("roll"))) }
        run(.1f); cam("entering"); run(4f); cam("entered")
        s.screenOn(true); run(.05f); cam("wake 0.05s"); run(1f); cam("lock")
        s.greet(); run(.8f); cam("unlock mid"); run(1.2f); cam("home p0")
        inp.pagePos = 1f; run(1.3f); cam("page 1"); inp.pagePos = 2f; run(1.3f); cam("page 2"); inp.pagePos = 7f; run(1.3f); cam("page 7(=2)")
        inp.audio.source = AudioFrame.Source.MUSIC; inp.audio.bass = .8f; run(2f); println("glyphs busy=" + s.busy)
        s.style(); 
    }
}
private fun StatueScene.style() { look = STATUE_LOOKS[2] }
