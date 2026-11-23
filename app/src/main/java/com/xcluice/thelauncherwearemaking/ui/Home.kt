package com.xcluice.thelauncherwearemaking.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xcluice.thelauncherwearemaking.AppInfo
import com.xcluice.thelauncherwearemaking.LauncherViewModel
import kotlin.math.abs

@Composable
fun HomePager(
    vm: LauncherViewModel,
    pagerState: PagerState,
    onClockClick: () -> Unit,
    onLaunch: (AppInfo, Rect) -> Unit,
    onMenu: (AppInfo, Rect, MenuOrigin) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val widthPx = ctx.resources.displayMetrics.widthPixels.toFloat()
    val cols = vm.settings.columns
    val iconDp = vm.settings.iconDp
    val labels = vm.settings.labels

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 1,
    ) { page ->
        val keys = vm.pages.getOrNull(page) ?: emptyList()
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            if (page == 0) {
                ClockHeader(
                    onClick = onClockClick,
                    modifier = Modifier.graphicsLayer {
                        val o = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        translationX = o * widthPx * 0.45f
                        alpha = 1f - abs(o).coerceAtMost(1f)
                    },
                )
            }
            var index = 0
            keys.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    for (c in 0 until cols) {
                        val app = row.getOrNull(c)?.let { vm.appMap[it] }
                        val i = index++
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (app != null) {
                                AppIcon(
                                    app = app,
                                    iconDp = iconDp,
                                    showLabel = labels,
                                    enterDelayMs = 40L + i * 28L,
                                    modifier = Modifier.graphicsLayer {
                                        val o = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                        val ao = abs(o).coerceAtMost(1f)
                                        translationX = o * widthPx * (0.12f + 0.1f * c)
                                        alpha = 1f - ao * 0.9f
                                        val s = 1f - ao * 0.22f
                                        scaleX = s
                                        scaleY = s
                                    },
                                    onClick = { r -> onLaunch(app, r) },
                                    onLongClick = { r -> onMenu(app, r, MenuOrigin.HOME) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PageDots(pagerState: PagerState, count: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = pagerState.currentPage == i
            val w by animateDpAsState(
                targetValue = if (active) 20.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                label = "dotW",
            )
            val a by animateFloatAsState(
                targetValue = if (active) 1f else 0.4f,
                label = "dotA",
            )
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = a)),
            )
        }
    }
}

@Composable
fun Dock(
    vm: LauncherViewModel,
    drawer: DrawerController,
    onLaunch: (AppInfo, Rect) -> Unit,
    onMenu: (AppInfo, Rect, MenuOrigin) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .graphicsLayer {
                translationY = drawer.progress * 240.dp.toPx()
                alpha = (1f - drawer.progress * 1.6f).coerceIn(0f, 1f)
            }
            .clip(shape)
            .background(Color(0x66000000))
            .border(1.dp, Color(0x24FFFFFF), shape)
            .heightIn(min = 76.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        vm.dock.mapNotNull { vm.appMap[it] }.forEach { app ->
            AppIcon(
                app = app,
                iconDp = vm.settings.iconDp,
                showLabel = false,
                onClick = { r -> onLaunch(app, r) },
                onLongClick = { r -> onMenu(app, r, MenuOrigin.DOCK) },
            )
        }
    }
}
