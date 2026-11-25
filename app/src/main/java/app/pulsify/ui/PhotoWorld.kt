package app.pulsify.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pulsify.core.Pulse
import java.io.File

/**
 * "Your photo": a picture from the phone becomes the creature's world. It is copied into the app
 * (so the wallpaper keeps it even if the original is deleted), turned upright, and kept no bigger
 * than a tall phone screen needs.
 */
object PhotoWorld {
    private const val MAX_H = 2400

    /** Copies [uri] in as the photo of this creature look. Returns false if it could not be read. */
    fun import(ctx: Context, uri: Uri, character: Int, style: Int): Boolean = try {
        val bmp = decodeUpright(ctx, uri) ?: throw IllegalStateException("unreadable")
        val name = Pulse.prefs.userBgFile(character, style)
        val tmp = File(ctx.filesDir, "$name.tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        val dst = File(ctx.filesDir, name)
        if (!tmp.renameTo(dst)) { tmp.copyTo(dst, overwrite = true); tmp.delete() }
        Pulse.prefs.bumpUserBg(character, style)
        true
    } catch (t: Throwable) {
        android.util.Log.w("Screenpals", "photo import failed", t); false
    }

    fun has(ctx: Context, character: Int, style: Int): Boolean = File(ctx.filesDir, Pulse.prefs.userBgFile(character, style)).exists()

    private fun decodeUpright(ctx: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = android.graphics.ImageDecoder.createSource(ctx.contentResolver, uri)
            return android.graphics.ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                val h = info.size.height; val w = info.size.width
                if (h > MAX_H) dec.setTargetSize((w * MAX_H.toFloat() / h).toInt().coerceAtLeast(1), MAX_H)
                dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (o.outHeight / (s * 2) >= MAX_H) s *= 2
        val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return null
        val deg = ctx.contentResolver.openInputStream(uri)?.use {
            when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (deg == 0f) return b
        val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg) }, true)
        if (r !== b) b.recycle()
        return r
    }
}

/**
 * Placing the creature on the user's photo, right on the live preview: drag anywhere and the
 * creature walks its home there as you move (its feet go where the ring is).
 */
@Composable
fun PlaceCreatureOverlay(state: AppState, onDone: () -> Unit) {
    val c = LocalPulse.current
    val ch = state.character; val st = state.style
    val start = remember(ch, st) { Pulse.prefs.userSpot(ch, st) }
    var x by remember(ch, st) { mutableFloatStateOf(start.first) }
    var y by remember(ch, st) { mutableFloatStateOf(start.second) }
    fun put(px: Float, py: Float) {
        x = px.coerceIn(0.08f, 0.92f); y = py.coerceIn(0.3f, 0.95f)
        Pulse.prefs.setUserSpot(ch, st, x, y)
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(ch, st) { detectTapGestures { o -> put(o.x / size.width, o.y / size.height) } }
                .pointerInput(ch, st) { detectDragGestures { ch2, _ -> put(ch2.position.x / size.width, ch2.position.y / size.height) } },
        ) {
            val px = x * size.width; val py = y * size.height
            // the ground line and a ring where the feet go
            drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, py), Offset(size.width, py), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())))
            drawCircle(c.accent.copy(alpha = 0.25f), radius = 26.dp.toPx(), center = Offset(px, py))
            drawCircle(Color.White, radius = 26.dp.toPx(), center = Offset(px, py), style = Stroke(2.dp.toPx()))
            drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(px, py))
        }
        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.statusBars).padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Place ${state.displayName}", style = T.title, color = c.text)
            Gap(6.dp)
            Text("Drag the ring to where it should stand on your photo.", style = T.small, color = c.sub, textAlign = TextAlign.Center)
        }
        GlowButton("Done", Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 18.dp)) { onDone() }
    }
}
