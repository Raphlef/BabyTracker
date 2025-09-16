package com.example.babytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    hazeState: HazeState  // pass Haze state from parent
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),       // more bottom space
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassIslandNavBar(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            navItems = navItems,
            hazeState = hazeState,          // provide for blur
            modifier = Modifier
                .wrapContentWidth()         // only as wide as needed
        )
        IslandFAB(
            onAddClicked = onAddClicked,
            hazeState = hazeState,
            modifier = Modifier
                .offset(y = (-40).dp)       // stronger floating
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
            .height(72.dp)
            .hazeEffect(// real backdrop blur
                state = hazeState,
                style = HazeStyle.Unspecified
            )
            .shadow(                       // floating shadow
                elevation = 16.dp,
                shape = RoundedCornerShape(36.dp)
            ),
        shape = RoundedCornerShape(36.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { tab ->
                NavigationBarItem(
                    icon = { tab.icon() },
                    label = { Text(tab.label,
                        style = MaterialTheme.typography.labelSmall) },
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
            .size(68.dp)
            .shadow(elevation = 20.dp, shape = CircleShape)
            // apply hazeChild here:
            .hazeEffect(
                state = hazeState,
                style = HazeStyle.Unspecified
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    ){
        Icon(Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onPrimary)
    }
}
