package app.pulsify.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StatueBirdTest {
    @Test fun flightPoseIsSaneAndMoves() {
        val f = listOf("src/main/assets/statues/mot.sta", "app/src/main/assets/statues/mot.sta").map(::File).first { it.exists() }
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertTrue(f.readBytes()[3] == '2'.code.toByte())
        b.position(36); val nb = b.int
        val par = IntArray(nb); val h = FloatArray(nb * 3)
        for (i in 0 until nb) { par[i] = b.int; h[i * 3] = b.float; h[i * 3 + 1] = b.float; h[i * 3 + 2] = b.float }
        assertTrue(nb <= StatueBird.MAX_BONES)
        val bird = StatueBird(); bird.setRig(par, h)
        val tipsY = ArrayList<Float>()
        var t = 0f
        repeat(120) {
            bird.update(1f / 60f, t, 0f); t += 1f / 60f
            bird.pose(0.3f)
            assertTrue(bird.mats.all { it.isFinite() && kotlin.math.abs(it) < 10f })
            // left wing tip pivot moved by its bone matrix
            val m = bird.mats; val i = 13; val x = h[i * 3]; val y = h[i * 3 + 1]; val z = h[i * 3 + 2]
            tipsY += m[i * 12 + 4] * x + m[i * 12 + 5] * y + m[i * 12 + 6] * z + m[i * 12 + 7]
        }
        // the wing really flaps: the tip travels a good way up and down in two seconds
        assertTrue("flap range ${tipsY.max() - tipsY.min()}", tipsY.max() - tipsY.min() > 0.4f)
        bird.kick(1f); bird.update(0.1f, t, 1f); bird.pose(0f)
        assertTrue(bird.mats.all { it.isFinite() })
    }
}
