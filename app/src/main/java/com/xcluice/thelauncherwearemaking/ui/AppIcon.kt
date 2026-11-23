package com.xcluice.thelauncherwearemaking.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcluice.thelauncherwearemaking.AppInfo
import kotlinx.coroutines.delay

private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 11.sp,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 6f),
)

/**
 * One app icon. No ripples or tap highlights — presses are expressed purely as a springy scale.
 * [enterDelayMs] < 0 disables the entrance animation.
 */
@Composable
fun AppIcon(
    app: AppInfo,
    iconDp: Int,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    enterDelayMs: Long = -1L,
    onClick: (Rect) -> Unit,
    onLongClick: (Rect) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val enter = remember { Animatable(if (enterDelayMs < 0) 1f else 0f) }
    LaunchedEffect(app.key) {
        if (enterDelayMs >= 0 && enter.value < 1f) {
            delay(enterDelayMs)
            enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
        }
    }
    val clickState = rememberUpdatedState(onClick)
    val longState = rememberUpdatedState(onLongClick)

    Column(
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .graphicsLayer {
                val s = press * (0.4f + 0.6f * enter.value)
                scaleX = s
                scaleY = s
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .pointerInput(app.key) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { clickState.value(bounds) },
                    onLongPress = { longState.value(bounds) },
                )
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(iconDp.dp),
        )
        if (showLabel) {
            BasicText(
                text = app.label,
                style = LabelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 5.dp)
                    .width((iconDp + 18).dp),
            )
        }
    }
}
