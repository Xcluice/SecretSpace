package com.xcluice.thelauncherwearemaking.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.xcluice.thelauncherwearemaking.VantaSettings
import kotlin.math.roundToInt

enum class MenuOrigin { HOME, DOCK, DRAWER }

data class MenuState(val app: com.xcluice.thelauncherwearemaking.AppInfo, val bounds: Rect, val origin: MenuOrigin)

class MenuItem(val label: String, val danger: Boolean = false, val action: () -> Unit)

private val Danger = Color(0xFFFF9C94)

// ---------------------------------------------------------------- context menu

@Composable
fun ContextMenu(state: MenuState, iconDp: Int, items: List<MenuItem>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val screenW = with(density) { cfg.screenWidthDp.dp.toPx() }
    val cardW = with(density) { 214.dp.toPx() }
    val rowH = with(density) { 50.dp.toPx() }
    val pad = with(density) { 12.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    val minTop = with(density) { 72.dp.toPx() }
    val cardH = rowH * items.size + pad
    val b = state.bounds
    val cx = b.center.x
    val x = (cx - cardW / 2f).coerceIn(margin, (screenW - cardW - margin).coerceAtLeast(margin))
    val above = b.top - cardH - pad >= minTop
    val y = if (above) b.top - cardH - pad else b.bottom + pad
    val originX = ((cx - x) / cardW).coerceIn(0f, 1f)
    val originY = if (above) 1f else 0f
    val iconPx = with(density) { iconDp.dp.toPx() }
    val iconTop = b.top + with(density) { 4.dp.toPx() }

    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 520f)) }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.6f * anim.value.coerceIn(0f, 1f))) }
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        // Lifted icon
        Image(
            bitmap = state.app.icon,
            contentDescription = null,
            modifier = Modifier
                .offset { IntOffset((cx - iconPx / 2f).roundToInt(), iconTop.roundToInt()) }
                .size(iconDp.dp)
                .graphicsLayer {
                    val s = lerp(1f, 1.14f, anim.value)
                    scaleX = s
                    scaleY = s
                },
        )
        Column(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(214.dp)
                .graphicsLayer {
                    val s = lerp(0.45f, 1f, anim.value)
                    scaleX = s
                    scaleY = s
                    alpha = anim.value.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(originX, originY)
                }
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF2161616))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(22.dp))
                .padding(vertical = 6.dp),
        ) {
            items.forEach { item ->
                MenuRow(item, onDone = onDismiss)
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, onDone: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "row",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer {
                scaleX = s
                scaleY = s
            }
            .pointerInput(item) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        item.action()
                        onDone()
                    },
                )
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            text = item.label,
            style = TextStyle(
                color = if (item.danger) Danger else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

// ---------------------------------------------------------------- settings sheet

@Composable
fun SettingsSheet(
    progress: State<Float>,
    settings: VantaSettings,
    hiddenCount: Int,
    onChange: (VantaSettings) -> Unit,
    onUnhide: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.55f * progress.value.coerceIn(0f, 1f))) }
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - progress.value.coerceIn(0f, 1f)) * size.height }
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color(0xF5121212))
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp)
                .padding(top = 14.dp, bottom = 22.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0x40FFFFFF)),
            )
            Spacer(Modifier.height(18.dp))
            BasicText("Vanta", style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Launcher settings", style = TextStyle(color = Color(0x99FFFFFF), fontSize = 13.sp))
            Spacer(Modifier.height(20.dp))

            SettingRow("Icon size") {
                val idx = when {
                    settings.iconDp <= 48 -> 0
                    settings.iconDp >= 64 -> 2
                    else -> 1
                }
                Segmented(listOf("S", "M", "L"), idx) { i ->
                    onChange(settings.copy(iconDp = intArrayOf(48, 56, 64)[i]))
                }
            }
            SettingRow("Grid columns") {
                Segmented(listOf("4", "5"), if (settings.columns >= 5) 1 else 0) { i ->
                    onChange(settings.copy(columns = if (i == 1) 5 else 4))
                }
            }
            SettingRow("App labels") {
                VToggle(settings.labels) { onChange(settings.copy(labels = it)) }
            }
            SettingRow("Haptic feedback") {
                VToggle(settings.haptics) { onChange(settings.copy(haptics = it)) }
            }
            if (hiddenCount > 0) {
                SettingRow("Hidden apps ($hiddenCount)") {
                    PressText("Restore all", onUnhide)
                }
            }
            Spacer(Modifier.height(10.dp))
            BasicText(
                "Vanta 0.2.0",
                style = TextStyle(color = Color(0x66FFFFFF), fontSize = 12.sp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(title, style = TextStyle(color = Color.White, fontSize = 16.sp))
        content()
    }
}

@Composable
private fun PressText(text: String, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "pt",
    )
    BasicText(
        text,
        style = TextStyle(color = Color(0xFFB4C0FF), fontSize = 15.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .graphicsLayer {
                scaleX = s
                scaleY = s
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            }
            .padding(8.dp),
    )
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier
            .width(54.dp * options.size)
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0x1FFFFFFF)),
    ) {
        val seg = maxWidth / options.size
        val x by animateDpAsState(
            targetValue = seg * selected,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
            label = "seg",
        )
        Box(
            Modifier
                .offset(x = x)
                .width(seg)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val c by animateColorAsState(
                    targetValue = if (i == selected) Color.Black else Color.White,
                    label = "segc",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(i) { detectTapGestures { onSelect(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        label,
                        style = TextStyle(color = c, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}

@Composable
private fun VToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "tg",
    )
    val track by animateColorAsState(if (checked) Color.White else Color(0x33FFFFFF), label = "tt")
    val knob by animateColorAsState(if (checked) Color.Black else Color(0xCCFFFFFF), label = "tk")
    Box(
        Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(track)
            .pointerInput(checked) { detectTapGestures { onChange(!checked) } }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(22.dp)
                .clip(CircleShape)
                .background(knob),
        )
    }
}
