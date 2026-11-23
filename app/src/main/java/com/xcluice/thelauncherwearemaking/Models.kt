package com.xcluice.thelauncherwearemaking

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class AppInfo(
    val key: String,
    val pkg: String,
    val cls: String,
    val label: String,
    val icon: ImageBitmap,
)

@Immutable
data class VantaSettings(
    val iconDp: Int = 56,
    val columns: Int = 4,
    val labels: Boolean = true,
    val haptics: Boolean = true,
)
