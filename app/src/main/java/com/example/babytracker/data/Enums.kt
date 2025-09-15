package com.example.babytracker.data

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.*


// Enum for different Diaper event types
enum class DiaperType(val displayName: String, val icon: ImageVector) {
    WET("Wet", Icons.Default.Opacity),
    DIRTY("Dirty", Icons.Default.Circle),
    MIXED("Mixed", Icons.Default.Merge),
    DRY("Dry", Icons.Default.InvertColorsOff)
}

enum class PoopConsistency(val displayName: String, val icon: ImageVector) {
    LIQUIDE("Liquide", Icons.Default.WaterDrop),
    SEMI_SOLIDE("Semi-solide", Icons.Default.Grain),
    SOLIDE("Solide", Icons.Default.Circle)
}
enum class PoopColor(val displayName: String, val colorValue: Color) {
    BROWN("Marron", Color(0xFF8B4513)),
    YELLOW("Jaune", Color(0xFFFFFF00)),
    GREEN("Vert",  Color(0xFF4CAF50)),
    OTHER("Autre", Color.Gray)
}

// Enum for Feeding types
enum class FeedType {
    BREAST_MILK,
    FORMULA,
    SOLID
}

// Enum for Breast side during feeding or pumping
enum class BreastSide {
    LEFT,
    RIGHT,
    BOTH
}