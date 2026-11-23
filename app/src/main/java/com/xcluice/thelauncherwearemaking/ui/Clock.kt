package com.xcluice.thelauncherwearemaking.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ClockHeader(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 40L)
        }
    }
    val is24 = DateFormat.is24HourFormat(ctx)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))

    Column(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 24.dp, vertical = 26.dp),
    ) {
        AnimatedContent(
            targetState = time,
            transitionSpec = {
                (slideInVertically { it / 3 } + fadeIn()) togetherWith
                    (slideOutVertically { -it / 3 } + fadeOut())
            },
            label = "clock",
        ) { t ->
            BasicText(
                text = t,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-2).sp,
                    shadow = Shadow(Color(0x66000000), Offset(0f, 3f), 10f),
                ),
            )
        }
        BasicText(
            text = date,
            style = TextStyle(
                color = Color(0xD9FFFFFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(Color(0x66000000), Offset(0f, 2f), 8f),
            ),
        )
    }
}
