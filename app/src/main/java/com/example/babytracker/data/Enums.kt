package com.example.babytracker.data

// Enum for different Diaper event types
enum class DiaperType {
    WET,
    DIRTY, // Could represent "poop"
    MIXED, // Both wet and dirty
    DRY
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