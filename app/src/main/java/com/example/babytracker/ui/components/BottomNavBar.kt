package com.example.babytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.hazeEffect


@Composable
fun BottomNavBar(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit,
    onAddClicked: () -> Unit,
    navItems: List<DashboardTab>,
    hazeState: HazeState
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
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onAddClicked,
        modifier = modifier
            .size(64.dp)
            .shadow(elevation = 20.dp, shape = CircleShape)
            // apply hazeChild here:
            .hazeEffect(
                state = hazeState,
                style = HazeStyle.Unspecified
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}
