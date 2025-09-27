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
enum class DrugType(val displayName: String, val icon: ImageVector,
                    val category: Category) {
    // Analgesics & Antipyretics
    PARACETAMOL("Paracetamol", Icons.Default.LocalHospital, Category.MEDICINE),

    // Vitamins
    VITAMIN_A("Vitamin A", Icons.Default.BabyChangingStation, Category.VITAMIN),
    VITAMIN_B("Vitamin B Complex", Icons.Default.Coffee, Category.VITAMIN),
    VITAMIN_C("Vitamin C", Icons.Default.WbTwilight, Category.VITAMIN),
    VITAMIN_D("Vitamin D", Icons.Default.WbSunny, Category.VITAMIN),
    VITAMIN_K("Vitamin K", Icons.Default.MoreHoriz, Category.VITAMIN),

    // Minerals & Others
    IRON("Iron Supplement", Icons.Default.MoreHoriz, Category.SUPPLEMENT),
    CALCIUM("Calcium Supplement", Icons.Default.MoreHoriz, Category.SUPPLEMENT),
    OTHER("Other", Icons.Default.MoreHoriz, Category.OTHER);

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