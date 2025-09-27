package com.kouloundissa.twinstracker.data

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
enum class FeedType(val icon: ImageVector) {
    BREAST_MILK(Icons.Default.Favorite),
    FORMULA(Icons.Default.LocalDrink),
    SOLID(Icons.Default.Restaurant)
}

// Enum for Breast side during feeding or pumping
enum class BreastSide(val icon: ImageVector){
    LEFT(Icons.Default.ChevronLeft),
    RIGHT(Icons.Default.ChevronRight),
    BOTH(Icons.Default.SwapHoriz)
}
enum class DrugType(val displayName: String, val icon: ImageVector) {
    PARACETAMOL("Paracetamol", Icons.Default.MedicalInformation),
    VITAMIN_D("Vitamine D", Icons.Default.WbSunny),
    OTHER("Other", Icons.Default.MoreHoriz);

    companion object {
        val entries = DrugType.entries
    }
}