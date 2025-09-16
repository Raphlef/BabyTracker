package com.example.babytracker.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.babytracker.presentation.dashboard.DashboardTab
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.hazeEffect
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin


@Composable
fun BottomNavBar(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit,
    onAddClicked: () -> Unit,
    navItems: List<DashboardTab>,
    hazeState: HazeState,
    eventTypes: List<Pair<String, @Composable () -> Unit>>,
    onEventTypeSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassIslandNavBar(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            navItems = navItems,
            hazeState = hazeState,
            modifier = Modifier
                .wrapContentWidth()          // only wrap content
                .padding(horizontal = 16.dp)  // small margin from screen edges
        )
        IslandFAB(
            onAddClicked = onAddClicked,
            hazeState = hazeState,
            eventTypes = eventTypes,
            onEventTypeSelected = onEventTypeSelected,
            modifier = Modifier
                .offset(y = (-36).dp)         // lift FAB slightly less
        )
    }
}


@Composable
fun GlassIslandNavBar(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit,
    navItems: List<DashboardTab>,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(64.dp)                    // reduce height for tighter island
            .hazeEffect(
                // updated API
                state = hazeState,
                style = HazeStyle.Unspecified,  // or HazeStyle.Light / Dark
            )
            .shadow(16.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, tab ->
                if (index == navItems.size / 2) {
                    Spacer(Modifier.width(48.dp)) // add gap around the FAB
                }
                NavigationBarItem(
                    icon = { tab.icon() },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}


@Composable
fun IslandFAB(
    onAddClicked: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    eventTypes: List<Pair<String, @Composable () -> Unit>>,
    onEventTypeSelected: (String) -> Unit
) {
    val fabSizeDp = 64.dp
    val iconSizeDp = 48.dp
    val fabRadiusPx: Float
    val iconRadiusPx: Float

    val density = LocalDensity.current
    with(density) {
        fabRadiusPx = fabSizeDp.toPx() / 2f
        iconRadiusPx = iconSizeDp.toPx() / 2f
    }
    var longPressActive by remember { mutableStateOf(false) }
    var selectedIconIndex by remember { mutableStateOf<Int?>(null) }
    var pointerPosition by remember { mutableStateOf<Offset?>(null) }

    // Angles for arc positions (in degrees)
    val arcAngles = remember(eventTypes.size) {
        val startAngle = -150f // 150 degrees to left-top corner
        val sweep = 120f // cover 120 degrees arc above FAB
        if (eventTypes.size == 1) listOf(-90f) else {
            // Equally space angles in arc from left-top to right-top
            List(eventTypes.size) { i -> startAngle + i * (sweep / (eventTypes.size - 1)) }
        }
    }

    Box(
        modifier = modifier
            .size(fabSizeDp)
            .pointerInput(longPressActive) {
                if (longPressActive) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position
                            pointerPosition = pos

                            if (event.changes.all { it.changedToUp() }) {
                                selectedIconIndex?.let { index ->
                                    onEventTypeSelected(eventTypes[index].first.uppercase(Locale.getDefault()))
                                }
                                longPressActive = false
                                selectedIconIndex = null
                                pointerPosition = null
                                break
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!longPressActive) {
                            onAddClicked()
                        }
                    },
                    onLongPress = {
                        longPressActive = true
                        selectedIconIndex = null
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Main FAB button stable (does not move)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            shadowElevation = 20.dp,
            modifier = Modifier.size(fabSizeDp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // Show arc menu above FAB on long press
        if (longPressActive) {
            eventTypes.forEachIndexed { index, (label, icon) ->
                val arcRadiusPx = with(density) { 140.dp.toPx() }
                val angleRad = Math.toRadians(arcAngles[index].toDouble())
                val offsetX = arcRadiusPx * cos(angleRad).toFloat()
                val offsetY =
                    arcRadiusPx * sin(angleRad).toFloat()// negative because y-down + pos origin

                val iconCenter =
                    Offset(fabRadiusPx + offsetX.toFloat(), fabRadiusPx + offsetY.toFloat())

                // Detect pointer hover over this icon
                val isSelected = pointerPosition?.let { pointer ->
                    val iconRect = Rect(
                        offset = iconCenter - Offset(iconRadiusPx, iconRadiusPx),
                        size = Size(iconRadiusPx * 2, iconRadiusPx * 2)
                    )
                    iconRect.contains(pointer)
                } ?: false

                if (isSelected) selectedIconIndex = index

                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.8f
                    ),
                    modifier = Modifier
                        .size(iconSizeDp)
                        .graphicsLayer {
                            translationX = offsetX.toFloat()
                            translationY = offsetY.toFloat()
                        },
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
            }
        }
    }
}