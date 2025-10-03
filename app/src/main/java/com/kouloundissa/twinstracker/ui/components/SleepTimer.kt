package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.ui.theme.*
import kotlinx.coroutines.delay

import kotlin.time.ExperimentalTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalTime::class)
@Composable
fun SleepTimer(
    sleepEvent: SleepEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var elapsedSeconds by remember { mutableStateOf(0L) }
    val colorContent = DarkBlue
    // Launch a ticker that updates every minute
    LaunchedEffect(sleepEvent) {
        while (true) {
            elapsedSeconds = Duration.between(
                sleepEvent.timestamp.toInstant(),
                Instant.now()
            ).seconds
            delay(1_000L)
        }
    }

    val h = elapsedSeconds / 3600
    val m = (elapsedSeconds % 3600) / 60
    val s = elapsedSeconds % 60
    val timeText = String.format("%02d:%02d:%02d", h, m, s)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorContent.copy(alpha = 0.75f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "\uD83D\uDCA4",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}