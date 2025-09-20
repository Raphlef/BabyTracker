package com.kouloundissa.twinstracker.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Auth : Screen("auth_route", "Auth", Icons.Filled.Lock)

    object BabySelection : Screen("baby_selection_route", "Select Baby", Icons.Filled.People)
    object AddBaby : Screen("add_baby", "Ajouter un bébé",Icons.Filled.Add)
    object Dashboard : Screen("dashboard_route", "Dashboard", Icons.Filled.Dashboard)
    object Feeding : Screen("feeding_screen", "Feeding", Icons.Filled.Fastfood)
    object Diaper : Screen("diaper_screen", "Diaper", Icons.Filled.ChildCare) // Replace with better icon
    object Sleep : Screen("sleep_screen", "Sleep", Icons.Filled.Hotel)        // Replace with better icon
    object Growth : Screen("growth_screen", "Growth", Icons.Filled.ShowChart)    // Replace with better icon

    object Calendar : Screen("calendar_screen", "Calendar", Icons.Default.CalendarMonth)
}