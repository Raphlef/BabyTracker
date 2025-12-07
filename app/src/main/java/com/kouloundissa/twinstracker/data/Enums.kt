package com.kouloundissa.twinstracker.data

import android.content.Context
import androidx.annotation.StringRes
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
import com.kouloundissa.twinstracker.R


enum class DiaperType(
    internal @StringRes val displayNameRes: Int,
    val icon: ImageVector,
    val color: Color
) {
    WET(R.string.diaper_type_wet, Icons.Default.Opacity, Color(0xFF4FC3F7)),
    DIRTY(R.string.diaper_type_dirty, Icons.Default.Circle, Color(0xFF8D6E63)),
    MIXED(R.string.diaper_type_mixed, Icons.Default.Merge, Color(0xFFAB47BC)),
    DRY(R.string.diaper_type_dry, Icons.Default.InvertColorsOff, Color(0xFFFFCA28))
}

fun DiaperType.getDisplayName(context: Context): String {
    return context.getString(this.displayNameRes)
}

enum class PoopConsistency(
    internal @StringRes val displayNameRes: Int,
    val icon: ImageVector,
    val color: Color
) {
    LIQUIDE(R.string.poop_consistency_liquide, Icons.Default.WaterDrop, Color(0xFF29B6F6)),
    SEMI_SOLIDE(R.string.poop_consistency_semi_solide, Icons.Default.Grain, Color(0xFFFFB74D)),
    SOLIDE(R.string.poop_consistency_solide, Icons.Default.Circle, Color(0xFF66BB6A))
}

fun PoopConsistency.getDisplayName(context: Context): String {
    return context.getString(this.displayNameRes)
}

enum class PoopColor(
    internal @StringRes val displayNameRes: Int,
    val color: Color
) {
    BROWN(R.string.poop_color_brown, Color(0xFF8B4513)),
    YELLOW(R.string.poop_color_yellow, Color(0xFFFFFF00)),
    GREEN(R.string.poop_color_green, Color(0xFF4CAF50)),
    OTHER(R.string.poop_color_other, Color.Gray);
}

fun PoopColor.getDisplayName(context: Context): String {
    return context.getString(this.displayNameRes)
}

// Enum for Feeding types
enum class FeedType(
    internal @StringRes val displayNameRes: Int,
    val icon: ImageVector,
    val color: Color
) {
    BREAST_MILK(R.string.feed_type_breast_milk, Icons.Default.Favorite, Color(0xFFEC407A)),
    FORMULA(R.string.feed_type_formula, Icons.Default.LocalDrink, Color(0xFFFFA726)),
    SOLID(R.string.feed_type_solid, Icons.Default.Restaurant, Color(0xFF78C850));

}

fun FeedType.getDisplayName(context: Context): String {
    return context.getString(this.displayNameRes)
}

// Enum for Breast side during feeding or pumping
enum class BreastSide(
    @StringRes val displayNameRes: Int,
    val icon: ImageVector,
    val color: Color
) {
    LEFT(R.string.breast_left, Icons.Default.ChevronLeft, Color(0xFF42A5F5)),
    RIGHT(R.string.breast_right, Icons.Default.ChevronRight, Color(0xFFEF5350)),
    BOTH(R.string.breast_both, Icons.Default.SwapHoriz, Color(0xFFAB47BC));

}

fun BreastSide.getDisplayName(context: Context): String {
    return context.getString(this.displayNameRes)
}

enum class DrugType(
    internal @StringRes val displayNameRes: Int,
    val icon: ImageVector,
    val category: Category,
    val color: Color
) {
    // Analgesics & Antipyretics
    PARACETAMOL(
        R.string.drug_type_paracetamol,
        Icons.Default.LocalHospital,
        Category.MEDICINE,
        Color(0xFFE91E63)
    ),

    // Vitamins
    VITAMIN_A(
        R.string.drug_type_vitamin_a,
        Icons.Default.BabyChangingStation,
        Category.VITAMIN,
        Color(0xFFFF6F00)
    ),
    VITAMIN_B(
        R.string.drug_type_vitamin_b,
        Icons.Default.Coffee,
        Category.VITAMIN,
        Color(0xFFFFA726)
    ),
    VITAMIN_C(
        R.string.drug_type_vitamin_c,
        Icons.Default.WbTwilight,
        Category.VITAMIN,
        Color(0xFFFFCA28)
    ),
    VITAMIN_D(
        R.string.drug_type_vitamin_d,
        Icons.Default.WbSunny,
        Category.VITAMIN,
        Color(0xFFFDD835)
    ),
    VITAMIN_K(
        R.string.drug_type_vitamin_k,
        Icons.Default.MoreHoriz,
        Category.VITAMIN,
        Color(0xFF66BB6A)
    ),

    // Minerals & Others
    IRON(
        R.string.drug_type_iron,
        Icons.Default.MoreHoriz,
        Category.SUPPLEMENT,
        Color(0xFF8D6E63)
    ),
    CALCIUM(
        R.string.drug_type_calcium,
        Icons.Default.MoreHoriz,
        Category.SUPPLEMENT,
        Color(0xFFBDBDBD)
    ),

    // Creams & Ointments
    CREAM(
        R.string.drug_type_cream,
        Icons.Default.Healing,
        Category.SUPPLEMENT,
        Color(0xFFF48FB1)
    ),
    OTHER(
        R.string.drug_type_other,
        Icons.Default.MoreHoriz,
        Category.OTHER,
        Color(0xFF9E9E9E)
    );

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

fun DrugType.getDisplayName(context: Context): String {
    return context.getString(this.displayNameRes)
}

enum class AnalysisRange(
    internal @StringRes val displayNameRes: Int,
    val days: Int
) {
    ONE_DAY(R.string.range_1_day, 1),
    THREE_DAYS(R.string.range_3_days, 3),
    ONE_WEEK(R.string.range_1_week, 7),
    TWO_WEEKS(R.string.range_2_weeks, 14),
    ONE_MONTH(R.string.range_1_month, 30),
    THREE_MONTHS(R.string.range_3_months, 90),
    CUSTOM(R.string.range_custom, -1)
}
fun AnalysisRange.getDisplayName(context: Context): String {
    return context.getString(this.displayNameRes)
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