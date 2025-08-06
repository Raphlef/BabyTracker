package com.example.babytracker.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare // Example Icon
import androidx.compose.material.icons.filled.Fastfood // Example Icon for Feeding
import androidx.compose.material.icons.filled.Hotel // Example Icon for Sleep
import androidx.compose.material.icons.filled.ShowChart // Example Icon for Growth

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.babytracker.R
import com.example.babytracker.presentation.navigation.Screen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.vector.ImageVector

// Data class to represent each tab item
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val screenRoute: String // Could be a route or just an identifier, for this option it's not strictly for nav
)
@Composable
fun BottomNavBar(

    navController: NavController, // Keep for other potential navigation actions
    screensForBottomNav: List<Screen>, // Pass the list of screens to display in the bar
    selectedScreen: Screen,           // The currently selected screen object
    onScreenSelected: (Screen) -> Unit, // Callback when a screen/tab is selected
    modifier: Modifier = Modifier
) {

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        screensForBottomNav.forEach { screen ->
            NavigationBarItem(
                selected = selectedScreen == screen, // Compare screen objects directly
                onClick = { onScreenSelected(screen) },
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
