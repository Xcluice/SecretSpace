package com.xcluice.thelauncherwearemaking.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.xcluice.thelauncherwearemaking.AppInfo
import com.xcluice.thelauncherwearemaking.LauncherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VantaRoot(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val drawer = remember { DrawerController(scope) }
    val pagerState = rememberPagerState { vm.pages.size }
    var menu by remember { mutableStateOf<MenuState?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val sheet = animateFloatAsState(
        targetValue = if (settingsOpen) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
        label = "sheet",
    )
    val drawerVisible by remember { derivedStateOf { drawer.progress > 0.001f } }
    val drawerOpen by remember { derivedStateOf { drawer.progress > 0.01f } }
    val sheetVisible by remember { derivedStateOf { sheet.value > 0.001f } }

    fun tick() {
        if (vm.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val onLaunch: (AppInfo, Rect) -> Unit = { app, rect ->
        launchApp(ctx, view, app, rect)
        if (drawer.progress > 0f) {
            scope.launch {
                delay(450)
                drawer.snapTo(0f)
            }
        }
    }
    val onMenu: (AppInfo, Rect, MenuOrigin) -> Unit = { app, rect, origin ->
        tick()
        menu = MenuState(app, rect, origin)
    }

    // Home button pressed while we are already visible.
    LaunchedEffect(vm.homeSignal) {
        if (vm.homeSignal > 0) {
            menu = null
            settingsOpen = false
            drawer.animateTo(0f)
            pagerState.animateScrollToPage(0)
        }
    }

    BackHandler(enabled = menu != null || settingsOpen || drawerOpen) {
        when {
            menu != null -> menu = null
            settingsOpen -> settingsOpen = false
            else -> drawer.animateTo(0f)
        }
    }

    val notifThreshold = with(density) { 80.dp.toPx() }
    var downAccum by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { dy ->
        downAccum += dy
        drawer.dragBy(dy)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { drawer.heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0x59000000),
                        0.28f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color(0x73000000),
                    )
                )
                val p = drawer.progress
                if (p > 0f) drawRect(Color.Black.copy(alpha = 0.86f * p))
            },
    ) {
        // ---- Home layer
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = drawer.progress
                    val s = 1f - 0.07f * p
                    scaleX = s
                    scaleY = s
                    alpha = (1f - p * 1.3f).coerceIn(0f, 1f)
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { downAccum = 0f },
                    onDragStopped = { v ->
                        if (drawer.progress > 0.001f) {
                            drawer.settle(v)
                        } else if (downAccum > notifThreshold) {
                            expandNotifications(ctx)
                        }
                    },
                )
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        tick()
                        settingsOpen = true
                    })
                },
        ) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                HomePager(
                    vm = vm,
                    pagerState = pagerState,
                    onClockClick = { openClock(ctx) },
                    onLaunch = onLaunch,
                    onMenu = onMenu,
                    modifier = Modifier.weight(1f),
                )
                PageDots(pagerState, vm.pages.size)
                Dock(vm, drawer, onLaunch, onMenu)
            }
        }

        // ---- Drawer layer
        if (drawerVisible) {
            DrawerLayer(vm = vm, drawer = drawer, onLaunch = onLaunch, onMenu = onMenu)
        }

        // ---- Settings sheet
        if (sheetVisible) {
            SettingsSheet(
                progress = sheet,
                settings = vm.settings,
                hiddenCount = vm.hidden.size,
                onChange = { vm.updateSettings(it) },
                onUnhide = { vm.unhideAll() },
                onClose = { settingsOpen = false },
            )
        }

        // ---- Long-press menu
        menu?.let { m ->
            val items = buildMenu(vm, ctx, m)
            ContextMenu(
                state = m,
                iconDp = vm.settings.iconDp,
                items = items,
                onDismiss = { menu = null },
            )
        }
    }
}

private fun buildMenu(
    vm: LauncherViewModel,
    ctx: android.content.Context,
    m: MenuState,
): List<MenuItem> {
    val a = m.app
    val list = mutableListOf<MenuItem>()
    when (m.origin) {
        MenuOrigin.HOME -> list += MenuItem("Remove from home") { vm.removeFromHome(a.key) }
        MenuOrigin.DOCK -> list += MenuItem("Remove from dock") { vm.removeFromDock(a.key) }
        MenuOrigin.DRAWER -> {
            if (vm.pages.any { a.key in it }) {
                list += MenuItem("Remove from home") { vm.removeFromHome(a.key) }
            } else {
                list += MenuItem("Add to home") { vm.addToHome(a.key) }
            }
            if (a.key !in vm.dock && vm.dock.size < 5) {
                list += MenuItem("Add to dock") { vm.addToDock(a.key) }
            }
            list += MenuItem("Hide app") { vm.hide(a.key) }
        }
    }
    list += MenuItem("App info") { openAppInfo(ctx, a.pkg) }
    list += MenuItem("Uninstall", danger = true) { uninstallApp(ctx, a.pkg) }
    return list
}
