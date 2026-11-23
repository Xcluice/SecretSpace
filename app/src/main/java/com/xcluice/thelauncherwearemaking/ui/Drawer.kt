package com.xcluice.thelauncherwearemaking.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcluice.thelauncherwearemaking.AppInfo
import com.xcluice.thelauncherwearemaking.LauncherViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Drives the app drawer open/close with finger tracking and spring settling. 0 = closed, 1 = open. */
@Stable
class DrawerController(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set
    var heightPx: Float = 1f
    private var job: Job? = null

    fun dragBy(dy: Float) {
        job?.cancel()
        progress = (progress - dy / heightPx).coerceIn(0f, 1f)
    }

    fun snapTo(value: Float) {
        job?.cancel()
        progress = value
    }

    fun animateTo(target: Float, velocityY: Float = 0f) {
        job?.cancel()
        job = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = -velocityY / heightPx,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
            ) { value, _ -> progress = value.coerceIn(0f, 1f) }
        }
    }

    fun settle(velocityY: Float) {
        val target = when {
            velocityY < -1200f -> 1f
            velocityY > 1200f -> 0f
            progress > 0.5f -> 1f
            else -> 0f
        }
        animateTo(target, velocityY)
    }
}

private class DrawerNested(private val drawer: DrawerController) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y < 0f && drawer.progress < 1f) {
            val before = drawer.progress
            drawer.dragBy(available.y)
            return Offset(0f, -(drawer.progress - before) * drawer.heightPx)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y > 0f && drawer.progress > 0f) {
            val before = drawer.progress
            drawer.dragBy(available.y)
            return Offset(0f, (before - drawer.progress) * drawer.heightPx)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drawer.progress > 0f && drawer.progress < 1f) {
            drawer.settle(available.y)
            return available
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (drawer.progress > 0f && available.y > 0f) {
            drawer.settle(available.y)
            return available
        }
        return Velocity.Zero
    }
}

@Composable
fun DrawerLayer(
    vm: LauncherViewModel,
    drawer: DrawerController,
    onLaunch: (AppInfo, Rect) -> Unit,
    onMenu: (AppInfo, Rect, MenuOrigin) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val gridState = rememberLazyGridState()
    val nested = remember(drawer) { DrawerNested(drawer) }
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    val apps = vm.apps
    val hidden = vm.hidden
    val list = remember(apps, hidden, query) {
        val q = query.trim()
        apps.filter { it.key !in hidden && (q.isEmpty() || it.label.contains(q, ignoreCase = true)) }
    }
    val cols = vm.settings.columns
    val iconDp = vm.settings.iconDp
    val labels = vm.settings.labels

    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - drawer.progress) * drawer.heightPx * 0.85f
                alpha = (drawer.progress * 1.8f).coerceIn(0f, 1f)
            }
            .nestedScroll(nested)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
            .padding(horizontal = 12.dp),
    ) {
        Box(Modifier.height(14.dp))
        SearchBar(
            query = query,
            onChange = { query = it },
            onGo = { list.firstOrNull()?.let { onLaunch(it, Rect.Zero) } },
        )
        Box(Modifier.height(8.dp))
        if (list.isEmpty() && query.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                BasicText(
                    text = "No apps found",
                    style = TextStyle(color = Color(0x99FFFFFF), fontSize = 15.sp),
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                state = gridState,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(list, key = { _, a -> a.key }) { index, app ->
                    Box(
                        Modifier.graphicsLayer {
                            val row = index / cols
                            val p = (drawer.progress * 1.5f - row * 0.05f).coerceIn(0f, 1f)
                            val s = 0.7f + 0.3f * p
                            scaleX = s
                            scaleY = s
                            alpha = p
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            app = app,
                            iconDp = iconDp,
                            showLabel = labels,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { r -> onLaunch(app, r) },
                            onLongClick = { r -> onMenu(app, r, MenuOrigin.DRAWER) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, onGo: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Color(0x26FFFFFF))
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            BasicText(
                text = "Search apps",
                style = TextStyle(color = Color(0x99FFFFFF), fontSize = 16.sp),
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
