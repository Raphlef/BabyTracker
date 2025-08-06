package com.example.babytracker.data.event

// Enum for different Diaper event types
enum class DiaperType {
    WET,
    DIRTY, // Could represent "poop"
    MIXED // Both wet and dirty
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