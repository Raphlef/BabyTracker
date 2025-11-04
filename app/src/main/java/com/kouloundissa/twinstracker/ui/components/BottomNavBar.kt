package com.kouloundissa.twinstracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.kouloundissa.twinstracker.data.DashboardTab
import com.kouloundissa.twinstracker.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin


@Composable
fun BottomNavBar(
    modifier: Modifier = Modifier,
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit,
    onAddClicked: () -> Unit,
    navItems: List<DashboardTab>,
    hazeState: HazeState,
    eventTypes: List<Pair<String, @Composable () -> Unit>>,
    onEventTypeSelected: (String) -> Unit
) {

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassIslandNavBar(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            navItems = navItems,
            hazeState = hazeState,
            modifier = Modifier.wrapContentWidth()
        )
        IslandFAB(
            onAddClicked = onAddClicked,
            hazeState = hazeState,
            eventTypes = eventTypes,
            onEventTypeSelected = onEventTypeSelected,
            modifier = Modifier.align(Alignment.Center)
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
    val baseColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue

    val cornerShape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = modifier.height(64.dp),
        color = baseColor.copy(alpha = 0.95f),
        shape = cornerShape
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            navItems.forEachIndexed { index, tab ->
                if (index == navItems.size / 2) {
                    Spacer(Modifier.width(64.dp))
                }

                val animatedScale by animateFloatAsState(
                    targetValue = if (selectedTab == tab) 1.1f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                    label = "nav_item_scale"
                )

                val animatedAlpha by animateFloatAsState(
                    targetValue = if (selectedTab == tab) 1f else 0.6f,
                    animationSpec = tween(200),
                    label = "nav_item_alpha"
                )
                NavigationBarItem(
                    icon = {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            tab.icon()
                        }
                    },
                    label = {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = animatedAlpha)
                        )
                    },
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = tint,
                        unselectedIconColor = contentColor.copy(alpha = 0.5f),
                        indicatorColor = tint.copy(alpha = 0.12f)
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
    onEventTypeSelected: (String) -> Unit,
) {
    val fabSizeDp = 54.dp
    val baseColor = BackgroundColor
    val contentColor = DarkGrey
    val tintColor = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val iconSizeDp = 54.dp
    val density = LocalDensity.current
    val fabRadiusPx = with(density) { fabSizeDp.toPx() / 2f }
    val iconRadiusPx = with(density) { iconSizeDp.toPx() / 2f }
    val arcRadiusPx = with(density) { 160.dp.toPx() }

    var longPressActive by remember { mutableStateOf(false) }
    var iconsVisible by remember { mutableStateOf(false) }
    var selectedIconIndex by remember { mutableStateOf<Int?>(null) }
    var pointerPosition by remember { mutableStateOf<Offset?>(null) }

    val arcAngles = remember(eventTypes.size) {
        val startAngle = -150f
        val sweep = 120f
        if (eventTypes.size == 1) listOf(-90f) else {
            List(eventTypes.size) { i -> startAngle + i * (sweep / (eventTypes.size - 1)) }
        }
    }

    Box(
        modifier = modifier
            .size(fabSizeDp)
            .pointerInput(Unit) {
                // Combined gesture detector to handle tap and long press in one block
                detectTapGestures(
                    onTap = {
                        if (!longPressActive) {
                            onAddClicked()
                        }
                    },
                    onLongPress = {
                        longPressActive = true
                        iconsVisible = true
                        selectedIconIndex = null
                    }
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        pointerPosition = event.changes.firstOrNull()?.position

                        if (longPressActive) {
                            if (event.changes.all { it.changedToUp() }) {
                                selectedIconIndex?.let { index ->
                                    onEventTypeSelected(eventTypes[index].first.uppercase(Locale.getDefault()))
                                }
                                longPressActive = false
                                iconsVisible = false
                                selectedIconIndex = null
                                pointerPosition = null
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Main FAB icon
        Surface(
            color = tintColor,
            shape = cornerShape,
            modifier = Modifier
                .size(fabSizeDp)               ,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = baseColor,
                )
            }
        }

        // Always compose sub icons but toggle visibility by alpha
        eventTypes.forEachIndexed { index, (label, icon) ->
            val angleRad = Math.toRadians(arcAngles[index].toDouble())
            val offsetX = arcRadiusPx * cos(angleRad).toFloat()
            val offsetY = arcRadiusPx * sin(angleRad).toFloat()

            val iconCenter = Offset(fabRadiusPx + offsetX, fabRadiusPx + offsetY)

            val isSelected = pointerPosition?.let { pointer ->
                val iconRect = Rect(
                    offset = iconCenter - Offset(iconRadiusPx, iconRadiusPx),
                    size = Size(iconRadiusPx * 2, iconRadiusPx * 2)
                )
                iconRect.contains(pointer)
            } ?: false

            if (isSelected) {
                selectedIconIndex = index
            }

            val animatedScale by animateFloatAsState(
                targetValue = if (isSelected) 1.15f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "icon_scale_$index"
            )
            Surface(
                shape = CircleShape,
                color = if (isSelected)
                    tintColor.copy(alpha = 0.5f)
                else
                    baseColor.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(iconSizeDp)
                    .graphicsLayer {
                        translationX = offsetX
                        translationY = offsetY
                        alpha = if (iconsVisible) 1f else 0f
                        scaleX = animatedScale
                        scaleY = animatedScale
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }
        }
    }
}
