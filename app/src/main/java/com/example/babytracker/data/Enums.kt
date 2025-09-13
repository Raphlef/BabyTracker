package com.example.babytracker.data

import androidx.compose.ui.graphics.Color

// Enum for different Diaper event types
enum class DiaperType {
    WET,
    DIRTY, // Could represent "poop"
    MIXED, // Both wet and dirty
    DRY
}

enum class PoopConsistency(val displayName: String) {
    LIQUIDE("Liquide"),
    SEMI_SOLIDE("Semi-solide"),
    SOLIDE("Solide")
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