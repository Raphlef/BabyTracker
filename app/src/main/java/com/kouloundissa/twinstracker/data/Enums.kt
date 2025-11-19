package com.kouloundissa.twinstracker.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InvertColorsOff
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector


// Enum for different Diaper event types
enum class DiaperType(val displayName: String, val icon: ImageVector, val color: Color) {
    WET("Wet", Icons.Default.Opacity, Color(0xFF4FC3F7)),
    DIRTY("Dirty", Icons.Default.Circle, Color(0xFF8D6E63)),
    MIXED("Mixed", Icons.Default.Merge, Color(0xFFAB47BC)),
    DRY("Dry", Icons.Default.InvertColorsOff, Color(0xFFFFCA28))
}

enum class PoopConsistency(val displayName: String, val icon: ImageVector, val color: Color) {
    LIQUIDE("Liquide", Icons.Default.WaterDrop, Color(0xFF29B6F6)),
    SEMI_SOLIDE("Semi-solide", Icons.Default.Grain, Color(0xFFFFB74D)),
    SOLIDE("Solide", Icons.Default.Circle, Color(0xFF66BB6A))
}

enum class PoopColor(val displayName: String, val color: Color) {
    BROWN("Marron", Color(0xFF8B4513)),
    YELLOW("Jaune", Color(0xFFFFFF00)),
    GREEN("Vert", Color(0xFF4CAF50)),
    OTHER("Autre", Color.Gray)
}

// Enum for Feeding types
enum class FeedType(val icon: ImageVector, val color: Color) {
    BREAST_MILK(Icons.Default.Favorite, Color(0xFFEC407A)),
    FORMULA(Icons.Default.LocalDrink, Color(0xFFFFA726)),
    SOLID(Icons.Default.Restaurant, Color(0xFF78C850))
}

// Enum for Breast side during feeding or pumping
enum class BreastSide(val icon: ImageVector, val color: Color) {
    LEFT(Icons.Default.ChevronLeft, Color(0xFF42A5F5)),
    RIGHT(Icons.Default.ChevronRight, Color(0xFFEF5350)),
    BOTH(Icons.Default.SwapHoriz, Color(0xFFAB47BC))
}

enum class DrugType(
    val displayName: String, val icon: ImageVector, val category: Category, val color: Color
) {
    // Analgesics & Antipyretics
    PARACETAMOL("Paracetamol", Icons.Default.LocalHospital, Category.MEDICINE, Color(0xFFE91E63)),

    // Vitamins
    VITAMIN_A("Vitamin A", Icons.Default.BabyChangingStation, Category.VITAMIN, Color(0xFFFF6F00)),
    VITAMIN_B("Vitamin B Complex", Icons.Default.Coffee, Category.VITAMIN, Color(0xFFFFA726)),
    VITAMIN_C("Vitamin C", Icons.Default.WbTwilight, Category.VITAMIN, Color(0xFFFFCA28)),
    VITAMIN_D("Vitamin D", Icons.Default.WbSunny, Category.VITAMIN, Color(0xFFFDD835)),
    VITAMIN_K("Vitamin K", Icons.Default.MoreHoriz, Category.VITAMIN, Color(0xFF66BB6A)),

    // Minerals & Others
    IRON("Iron Supplement", Icons.Default.MoreHoriz, Category.SUPPLEMENT, Color(0xFF8D6E63)),
    CALCIUM("Calcium Supplement", Icons.Default.MoreHoriz, Category.SUPPLEMENT, Color(0xFFBDBDBD)),

    // Creams & Ointments
    CREAM("Cream", Icons.Default.Healing, Category.SUPPLEMENT, Color(0xFFF48FB1)),
    OTHER("Other", Icons.Default.MoreHoriz, Category.OTHER, Color(0xFF9E9E9E));

    enum class Category {
        MEDICINE,
        VITAMIN,
        SUPPLEMENT,
        OTHER
    }

    companion object {
        val entries = DrugType.entries
    }
}

enum class DashboardTab(val label: String, val icon: @Composable () -> Unit) {
    Home("Home", { Icon(Icons.Default.Home, contentDescription = "Home") }),
    Calendar("Calendar", { Icon(Icons.Default.CalendarToday, contentDescription = "Calendar") }),
    Analysis(
        "Analysis",
        { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Analysis") }),
   // Settings("Settings", { Icon(Icons.Default.Settings, contentDescription = "Settings") }),
    Baby("Baby", { Icon(Icons.Default.ChildCare, contentDescription = "Baby") }),
}