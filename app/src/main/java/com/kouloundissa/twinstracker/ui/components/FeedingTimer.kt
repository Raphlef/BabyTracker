package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import kotlinx.coroutines.delay
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun FeedingTimer(
    nextFeedingTimeMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var millisecondsUntil by remember { mutableStateOf(0L) }
    val colorContent = DarkBlue

    LaunchedEffect(nextFeedingTimeMs) {
        while (true) {
            millisecondsUntil = maxOf(0, nextFeedingTimeMs - System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val hours = millisecondsUntil / (1000 * 60 * 60)
    val minutes = (millisecondsUntil % (1000 * 60 * 60)) / (1000 * 60)
    val seconds = (millisecondsUntil % (1000 * 60)) / 1000

    val timeText = if (millisecondsUntil > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        "Now!"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorContent.copy(alpha = 0.75f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "🍼",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelLarge,
            color = BackgroundColor
        )
    }
}
