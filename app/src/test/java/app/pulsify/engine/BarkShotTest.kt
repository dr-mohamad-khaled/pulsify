package app.pulsify.engine

import android.graphics.Bitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BarkShotTest {
    @Test fun bark() {
        val b = Bark.bitmap()
        FileOutputStream("/tmp/bark.png").use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
