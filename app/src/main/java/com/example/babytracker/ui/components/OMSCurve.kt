package com.example.babytracker.ui.components

import com.example.babytracker.data.Gender


/**
 * WHO weight-for-age percentiles at weekly intervals (0–8 weeks) for girls and boys.
 * Source: WHO Child Growth Standards.
 */


/** Returns (day, kg) pairs for given gender and percentile. */
fun WHOWeightCurve(
    gender: Gender,
    percentile: Int
): List<Pair<Float, Float>> = when (gender) {
    Gender.FEMALE -> when (percentile) {
        15 -> listOf(
            0f to 2.7f, 7f to 3.3f, 14f to 3.9f, 21f to 4.3f, 28f to 4.6f,
            35f to 4.9f, 42f to 5.2f, 49f to 5.4f, 56f to 5.7f
        )
        50 -> listOf(
            0f to 3.2f, 7f to 3.9f, 14f to 4.5f, 21f to 5.0f, 28f to 5.5f,
            35f to 5.9f, 42f to 6.3f, 49f to 6.6f, 56f to 6.9f
        )
        85 -> listOf(
            0f to 3.7f, 7f to 4.5f, 14f to 5.2f, 21f to 5.8f, 28f to 6.3f,
            35f to 6.8f, 42f to 7.2f, 49f to 7.6f, 56f to 8.0f
        )
        else -> emptyList()
    }
    else -> when (percentile) {
        15 -> listOf(
            0f to 2.9f, 7f to 3.6f, 14f to 4.2f, 21f to 4.7f, 28f to 5.1f,
            35f to 5.4f, 42f to 5.7f, 49f to 6.0f, 56f to 6.3f
        )
        50 -> listOf(
            0f to 3.3f, 7f to 4.1f, 14f to 4.8f, 21f to 5.3f, 28f to 5.8f,
            35f to 6.2f, 42f to 6.6f, 49f to 6.9f, 56f to 7.2f
        )
        85 -> listOf(
            0f to 3.8f, 7f to 4.7f, 14f to 5.4f, 21f to 6.0f, 28f to 6.5f,
            35f to 6.9f, 42f to 7.3f, 49f to 7.7f, 56f to 8.1f
        )
        else -> emptyList()
    }
}

/** Returns (day, cm) pairs for length (height) at given gender and percentile. */
fun WHOLengthCurve(
    gender: Gender,
    percentile: Int
): List<Pair<Float, Float>> = when (gender) {
    Gender.FEMALE -> when (percentile) {
        15 -> listOf(
            0f to 47.0f, 7f to 49.0f, 14f to 50.7f, 21f to 52.1f, 28f to 53.4f,
            35f to 54.6f, 42f to 55.7f, 49f to 56.7f, 56f to 57.7f
        )
        50 -> listOf(
            0f to 49.1f, 7f to 51.1f, 14f to 52.8f, 21f to 54.2f, 28f to 55.5f,
            35f to 56.7f, 42f to 57.8f, 49f to 58.8f, 56f to 59.8f
        )
        85 -> listOf(
            0f to 51.2f, 7f to 53.2f, 14f to 54.9f, 21f to 56.3f, 28f to 57.6f,
            35f to 58.8f, 42f to 59.9f, 49f to 60.9f, 56f to 61.9f
        )
        else -> emptyList()
    }
    else -> when (percentile) {
        15 -> listOf(
            0f to 47.1f, 7f to 49.3f, 14f to 51.1f, 21f to 52.5f, 28f to 53.8f,
            35f to 55.0f, 42f to 56.1f, 49f to 57.1f, 56f to 58.2f
        )
        50 -> listOf(
            0f to 49.9f, 7f to 51.9f, 14f to 53.7f, 21f to 55.1f, 28f to 56.4f,
            35f to 57.6f, 42f to 58.8f, 49f to 59.9f, 56f to 60.9f
        )
        85 -> listOf(
            0f to 52.6f, 7f to 54.6f, 14f to 56.4f, 21f to 57.8f, 28f to 59.1f,
            35f to 60.3f, 42f to 61.5f, 49f to 62.6f, 56f to 63.6f
        )
        else -> emptyList()
    }
}

/** Returns (day, cm) pairs for head circumference at given gender and percentile. */
fun WHOHeadCircumferenceCurve(
    gender: Gender,
    percentile: Int
): List<Pair<Float, Float>> = when (gender) {
    Gender.FEMALE -> when (percentile) {
        15 -> listOf(
            0f to 32.5f, 7f to 34.0f, 14f to 35.0f, 21f to 35.8f, 28f to 36.5f,
            35f to 37.1f, 42f to 37.6f, 49f to 38.1f, 56f to 38.5f
        )
        50 -> listOf(
            0f to 34.1f, 7f to 35.5f, 14f to 36.5f, 21f to 37.3f, 28f to 38.0f,
            35f to 38.6f, 42f to 39.1f, 49f to 39.6f, 56f to 40.0f
        )
        85 -> listOf(
            0f to 35.6f, 7f to 36.9f, 14f to 37.9f, 21f to 38.7f, 28f to 39.4f,
            35f to 40.0f, 42f to 40.5f, 49f to 41.0f, 56f to 41.4f
        )
        else -> emptyList()
    }
    else -> when (percentile) {
        15 -> listOf(
            0f to 32.6f, 7f to 34.2f, 14f to 35.2f, 21f to 36.0f, 28f to 36.7f,
            35f to 37.3f, 42f to 37.8f, 49f to 38.3f, 56f to 38.7f
        )
        50 -> listOf(
            0f to 34.5f, 7f to 36.0f, 14f to 37.0f, 21f to 37.8f, 28f to 38.5f,
            35f to 39.1f, 42f to 39.6f, 49f to 40.1f, 56f to 40.5f
        )
        85 -> listOf(
            0f to 36.3f, 7f to 37.7f, 14f to 38.7f, 21f to 39.5f, 28f to 40.2f,
            35f to 40.8f, 42f to 41.3f, 49f to 41.8f, 56f to 42.2f
        )
        else -> emptyList()
    }
}