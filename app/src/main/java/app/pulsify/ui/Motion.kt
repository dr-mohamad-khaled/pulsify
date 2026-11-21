package app.pulsify.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Staggered entrance: rises and fades in once. The stagger is part of the animation spec (not a
 * coroutine delay), and when the phone has animations switched off the content simply appears,
 * so nothing can ever stay invisible.
 */
fun Modifier.enter(index: Int, step: Long = 55L): Modifier = composed {
    if (!ValueAnimator.areAnimatorsEnabled()) return@composed this
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val a by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(durationMillis = 520, delayMillis = (index * step).toInt(), easing = FastOutSlowInEasing),
        label = "enter",
    )
    graphicsLayer {
        alpha = a
        translationY = (1f - a) * 48f
    }
}
