package com.example.babytracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.babytracker.presentation.dashboard.DashboardTab

@Composable
fun BottomNavBar(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit,
    onAddClicked: () -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        NavigationBar(
            tonalElevation = 3.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            @Composable
            fun navItem(tab: DashboardTab) {
                NavigationBarItem(
                    icon = { tab.icon() },
                    label = {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    alwaysShowLabel = true,
                )
            }
            // Spacer at start to push Home away from left edge
            Spacer(Modifier.width(16.dp))
            // Home
            navItem(DashboardTab.Home)

            Spacer(Modifier.weight(1f))

            // Calendar
            navItem(DashboardTab.Calendar)

            // Extra gap before FAB
            Spacer(Modifier.weight(4f))

            // Analysis
            navItem(DashboardTab.Analysis)

            Spacer(Modifier.weight(1f))

            // Settings
            navItem(DashboardTab.Settings)

            // Spacer at end to push Settings away from right edge
            Spacer(Modifier.width(16.dp))
        }

        FloatingActionButton(
            onClick = onAddClicked,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-28).dp)
                .size(60.dp)
                .shadow(8.dp, CircleShape, clip = false)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Event", Modifier.size(28.dp))
        }
    }
}