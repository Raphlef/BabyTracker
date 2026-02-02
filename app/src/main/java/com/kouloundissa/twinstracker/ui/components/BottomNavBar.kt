package com.kouloundissa.twinstracker.ui.components

import android.view.ViewConfiguration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.data.DashboardTab
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onEventTypeSelected: (String) -> Unit,
    onTabDoubleClick: ((DashboardTab) -> Unit)? = null,
    onTabLongPress: ((DashboardTab) -> Unit)? = null
) {

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassIslandNavBar(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            navItems = navItems,
            hazeState = hazeState,
            onTabDoubleClick = onTabDoubleClick,
            onTabLongPress = onTabLongPress,
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
    modifier: Modifier = Modifier,
    onTabDoubleClick: ((DashboardTab) -> Unit)? = null,
    onTabLongPress: ((DashboardTab) -> Unit)? = null
) {
    val baseColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue

    val cornerShape = MaterialTheme.shapes.extraLarge

    var lastClickTime by remember { mutableStateOf(0L) }
    var clickJob by remember { mutableStateOf<Job?>(null) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.height(66.dp),
        color = baseColor.copy(alpha = 0.99f),
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
                                }
                                .then(
                                    if (onTabDoubleClick != null || onTabLongPress != null) {
                                        Modifier.combinedClickable(
                                            onClick = {
                                                val currentTime = System.currentTimeMillis()
                                                val timeDiff = currentTime - lastClickTime

                                                if (onTabDoubleClick != null && timeDiff < ViewConfiguration.getDoubleTapTimeout()) {
                                                    // Double click detected!
                                                    clickJob?.cancel() // Cancel pending single click
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onTabDoubleClick(tab)
                                                    lastClickTime =
                                                        0L // Reset to avoid triple click
                                                } else {
                                                    // Potential first click of double click
                                                    lastClickTime = currentTime

                                                    // Delay single click action to allow for double click
                                                    clickJob?.cancel()
                                                    clickJob = scope.launch {
                                                        delay(
                                                            ViewConfiguration.getDoubleTapTimeout()
                                                                .toLong()
                                                        ) // Wait for potential second click
                                                        // If we reach here, it was a single click
                                                        onTabSelected(tab)
                                                    }
                                                }
                                            },
                                            onLongClick = onTabLongPress?.let {
                                                {
                                                    clickJob?.cancel() // Cancel any pending clicks
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    it(tab)
                                                    lastClickTime = 0L // Reset click tracking
                                                }
                                            },
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            tab.icon()
                        }
                    },
                    selected = selectedTab == tab,
                    onClick = {
                        // This handles the base navigation when no enhanced gestures are set
                        if (onTabDoubleClick == null && onTabLongPress == null) {
                            onTabSelected(tab)
                        }
                    },
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
    val iconSizeDp = 54.dp
    val arcRadiusDp = 160.dp

    val density = LocalDensity.current
    val fabRadiusPx = with(density) { fabSizeDp.toPx() / 2f }
    val iconRadiusPx = with(density) { iconSizeDp.toPx() / 2f }
    val arcRadiusPx = with(density) { arcRadiusDp.toPx() }

         var isExpanded by remember { mutableStateOf(false) }
    var selectedIconIndex by remember { mutableIntStateOf(-1) }
    var pointerPosition by remember { mutableStateOf(Offset.Zero) }

    val arcAngles = remember(eventTypes.size) {
        val startAngle = -150f
        val sweep = 120f
        if (eventTypes.size == 1) {
            listOf(-90f)
        } else {
            List(eventTypes.size) { i ->
                startAngle + i * (sweep / (eventTypes.size - 1))
            }
        }
    }

    fun calculateIconPosition(angleRad: Double): Offset {
        return Offset(
            x = arcRadiusPx * cos(angleRad).toFloat(),
            y = arcRadiusPx * sin(angleRad).toFloat()
        )
    }

    fun findSelectedIcon(pointer: Offset): Int {
        return eventTypes.indices.firstOrNull { index ->
            val angleRad = Math.toRadians(arcAngles[index].toDouble())
            val iconCenter = calculateIconPosition(angleRad)
            val distance = (pointer - (iconCenter + Offset(fabRadiusPx, fabRadiusPx))).getDistance()
            distance <= iconRadiusPx
        } ?: -1
    }
    val baseColor = BackgroundColor
    val contentColor = DarkGrey
    val tintColor = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    Box(
        modifier = modifier
            .size(fabSizeDp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (!isExpanded) onAddClicked() },
                    onLongPress = { isExpanded = true }
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()

                        if (isExpanded) {
                            event.changes.firstOrNull()?.position?.let { pos ->
                                pointerPosition = pos
                                selectedIconIndex = findSelectedIcon(pos)
                            }

                            if (event.changes.all { it.changedToUp() }) {
                                if (selectedIconIndex >= 0) {
                                    onEventTypeSelected(
                                        eventTypes[selectedIconIndex].first.uppercase()
                                    )
                                }
                                isExpanded = false
                                selectedIconIndex = -1
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
                .size(fabSizeDp),
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
        if (isExpanded) {
            eventTypes.forEachIndexed { index, (label, icon) ->
                val angleRad = Math.toRadians(arcAngles[index].toDouble())
                val offset = calculateIconPosition(angleRad)
                val isSelected = selectedIconIndex == index

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "scale_$index"
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
                            translationX = offset.x
                            translationY = offset.y
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
            }
        }
    }
}
