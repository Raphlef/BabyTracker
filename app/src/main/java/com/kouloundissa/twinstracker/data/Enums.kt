package com.kouloundissa.twinstracker.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InvertColorsOff
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.Sick
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Transgender
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.firestore.IgnoreExtraProperties
import com.kouloundissa.twinstracker.R
import java.util.UUID


enum class DiaperType(
    internal @StringRes val displayNameRes: Int,
    val icon: ImageVector,
    val color: Color,
    val emoji: String,
) {
    WET(R.string.diaper_type_wet, Icons.Default.Opacity, Color(0xFF4FC3F7), "💧"),
    DIRTY(R.string.diaper_type_dirty, Icons.Default.Circle, Color(0xFF8D6E63), "💩"),
    MIXED(R.string.diaper_type_mixed, Icons.Default.Merge, Color(0xFFAB47BC), "🔄"),
    DRY(R.string.diaper_type_dry, Icons.Default.InvertColorsOff, Color(0xFFFFCA28), "✓")
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

@IgnoreExtraProperties
data class CustomDrugType(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",          // display name
    val color: Long = 0xFF9E9E9E,    // ARGB hex stored as Long
    val iconName: String = "pill"   // key for mapping to ImageVector in app
)
data class DrugTypeUiModel(
    val id: String,          // for built-ins: enum.name, for custom: custom.id
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val isCustom: Boolean,
    val backingEnum: DrugType? = null,      // non-null when built-in
    val backingCustomId: String? = null     // non-null when custom
)
fun DrugType.toUiModel(context: Context): DrugTypeUiModel = DrugTypeUiModel(
    id = name,
    label = context.getString(displayNameRes),
    icon = icon,
    color = color,
    isCustom = false,
    backingEnum = this
)

fun CustomDrugType.toUiModel(): DrugTypeUiModel = DrugTypeUiModel(
    id = id,
    label = name,
    icon = iconName.toImageVector(),  // extension to map string -> ImageVector
    color = Color(color.toInt()),
    isCustom = true,
    backingCustomId = id
)
fun String.toImageVector(): ImageVector {
    return drugIconOptions
        .firstOrNull { it.key == this }
        ?.icon
        ?: Icons.Default.Medication
}
data class DrugIconOption(
    val key: String,
    val icon: ImageVector
)

val drugIconOptions = listOf(
    // 💊 Medical / Treatment
    DrugIconOption("pill", Icons.Default.MedicalServices),
    DrugIconOption("prescription", Icons.AutoMirrored.Filled.ReceiptLong),
    DrugIconOption("suppository", Icons.Default.Sick),
    DrugIconOption("vaccines", Icons.Default.Vaccines),
    DrugIconOption("healing", Icons.Default.Healing),
    DrugIconOption("blood", Icons.Default.Bloodtype),
    DrugIconOption("monitor", Icons.Default.MonitorHeart),
    DrugIconOption("science", Icons.Default.Science),
    DrugIconOption("antibiotic", Icons.Default.BugReport),

    // 🌬️ Respiratory & nebulizer (no Air reuse)
    DrugIconOption("cough", Icons.Default.Mic),
    DrugIconOption("nebulizer", Icons.Default.LocalFlorist),
    DrugIconOption("inhaler", Icons.Default.Air),

    // 👂 Eye/ear & digestion (unique)
    DrugIconOption("ear-eye-drop", Icons.Default.VisibilityOff),

    // 👶 Baby care & hygiene
    DrugIconOption("changing", Icons.Default.BabyChangingStation),
    DrugIconOption("bath", Icons.Default.Bathtub),

    // ☕ Nutrition & hydration
    DrugIconOption("coffee", Icons.Default.Coffee),
    DrugIconOption("nutrition", Icons.Default.Restaurant),
    DrugIconOption("water", Icons.Default.WaterDrop),

    // 😌 Wellness & spa
    DrugIconOption("spa", Icons.Default.Spa),
    DrugIconOption("favorite", Icons.Default.Favorite),
    DrugIconOption("weight", Icons.Default.FitnessCenter),

    // 🌞 Sleep & daily rhythm
    DrugIconOption("sleep", Icons.Default.Bedtime),
    DrugIconOption("sun", Icons.Default.WbSunny),
    DrugIconOption("moon", Icons.Default.DarkMode),
    DrugIconOption("Twilight", Icons.Default.WbTwilight),

    // 😊 Emotions & mood
    DrugIconOption("mood", Icons.Default.SentimentSatisfied),
    DrugIconOption("crying", Icons.Default.SentimentVeryDissatisfied),
    DrugIconOption("smile", Icons.Default.Mood),

    // 🌡️ Monitoring & environment
    DrugIconOption("temperature", Icons.Default.Thermostat),
    DrugIconOption("alert", Icons.Default.Warning),

    // ⏰ Time & reminders
    DrugIconOption("clock", Icons.Default.Alarm),
    DrugIconOption("calendar", Icons.Default.CalendarToday),
    DrugIconOption("check", Icons.Default.CheckCircle),
)
enum class DrugType(
    internal @StringRes val displayNameRes: Int,
    val icon: ImageVector,
    val color: Color
) {
    // Analgesics & Antipyretics
    PARACETAMOL(
        R.string.drug_type_paracetamol,
        Icons.Default.LocalHospital,
        Color(0xFFE91E63)
    ),
    VITAMIN_D(
        R.string.drug_type_vitamin_d,
        Icons.Default.WbSunny,
        Color(0xFFFDD835)
    ),
    VITAMIN_K(
        R.string.drug_type_vitamin_k,
        Icons.Default.MoreHoriz,
        Color(0xFF66BB6A)
    ),

    // Creams & Ointments
    CREAM(
        R.string.drug_type_cream,
        Icons.Default.Healing,
        Color(0xFFF48FB1)
    ),
    CUSTOM(
        R.string.custom,
        Icons.Default.Add,
        Color(0xFF9E9E9E)
    );

    companion object {
        val entries = DrugType.entries
    }
}

fun DrugType.getDisplayName(context: Context): String {
    return context.getString(this.displayNameRes)
}
enum class TreatmentFrequencyType(
    @StringRes val displayNameRes: Int,
    val icon: ImageVector,
    val color: Color
) {
    HOURLY(R.string.hourly, Icons.Default.AccessTime, Color.Blue),
    DAILY(R.string.daily, Icons.Default.DateRange, Color.Green),
    WEEKLY(R.string.weekly, Icons.Default.Schedule, Color(0xFFFF9800))
}
fun TreatmentFrequencyType.getDisplayName(context: Context): String {
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
    CUSTOM(R.string.custom, -1)
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

@IgnoreExtraProperties
enum class Gender(
    val icon: ImageVector,
    internal @StringRes val displayNameRes: Int,
    val emoji: String,
    val color: Color
) {
    MALE(
        Icons.Filled.Male,
        R.string.gender_male,
        "\uD83D\uDC66",
        Color(0xFF42A5F5)
    ),                          // Blue
    FEMALE(
        Icons.Filled.Female,
        R.string.gender_female,
        "\uD83D\uDC67",
        Color(0xFFEC407A)
    ),                        // Pink
    OTHER(
        Icons.Filled.Transgender,
        R.string.gender_other,
        "\uD83E\uDDD1\u200D\uD83E\uDDB1",
        Color(0xFFAB47BC)
    ),  // Purple
    PREFER_NOT_TO_SAY(
        Icons.Filled.VisibilityOff,
        R.string.gender_prefer_not_to_say,
        "❓",
        Color(0xFFFFCA28)
    ),   // Amber
    UNKNOWN(
        Icons.AutoMirrored.Filled.HelpOutline,
        R.string.gender_unknown,
        "❔",
        Color(0xFF9E9E9E)
    )         // Gray

}

fun Gender.getDisplayName(context: Context): String {
    return context.getString(this.displayNameRes)
}

enum class BloodType(val icon: ImageVector, val color: Color) {
    A(Icons.Filled.Bloodtype, Color(0xFFEF5350)),           // Red
    B(Icons.Filled.Bloodtype, Color(0xFFFFB74D)),           // Orange
    AB(Icons.Filled.Bloodtype, Color(0xFF66BB6A)),          // Green
    O(Icons.Filled.Bloodtype, Color(0xFF29B6F6)),           // Blue
    UNKNOWN(Icons.AutoMirrored.Filled.HelpOutline, Color(0xFF9E9E9E))  // Gray
}