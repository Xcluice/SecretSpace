package com.xcluice.secretspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0E0B1F)
val Violet = Color(0xFF7C4DFF)
val Pink = Color(0xFFFF4D8D)
val Panel = Color(0x1AFFFFFF)

val GradMain = Brush.linearGradient(listOf(Violet, Pink))
val GradBg = Brush.verticalGradient(listOf(Color(0xFF1C1547), Ink))

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet,
            secondary = Pink,
            background = Ink,
            surface = Color(0xFF17132E),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(16.dp)
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.padding(top = 8.dp), content = content)
    }
}

@Composable
fun GradButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(GradMain)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
