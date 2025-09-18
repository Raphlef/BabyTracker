import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.babytracker.data.Event
import com.example.babytracker.data.EventType
import java.time.ZoneId
import kotlin.collections.forEach

@Composable
fun TimelineBar(
    events: List<Event>,
    onEdit: (Event) -> Unit
) {
    val horizontalPadding = 8.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    ) {
        val totalWidth = maxWidth
        val hourSlotWidth = totalWidth / 24f
        Column(modifier = Modifier.fillMaxWidth()) {
            // Hour guidelines at the top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                repeat(24) { hr ->
                    Box(modifier = Modifier.width(hourSlotWidth)) {
                        when {
                            hr % 6 == 0 -> {
                                Divider(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .align(Alignment.CenterStart),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }

                            hr % 3 == 0 -> {
                                Divider(
                                    modifier = Modifier
                                        .width(0.5.dp)
                                        .fillMaxHeight()
                                        .align(Alignment.CenterStart),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            // Hour markers row
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(24) { hr ->
                    Box(
                        modifier = Modifier
                            .width(hourSlotWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hr % 3 == 0) {
                            Text(
                                text = hr.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Timeline background with events
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.CenterStart
            ) {
                events.forEach { evt ->
                    val startInstant = (evt as? com.example.babytracker.data.SleepEvent)
                        ?.beginTime ?: evt.timestamp
                    val zonedStart = startInstant.toInstant().atZone(ZoneId.systemDefault())
                    val startHourFloat = zonedStart.hour + zonedStart.minute / 60f

                    // 2. Compute durationHours and keep startHourFloat unchanged
                    val durationHours = if (evt is com.example.babytracker.data.SleepEvent) {
                        val endInstant = evt.endTime ?: evt.beginTime ?: evt.timestamp
                        val zonedEnd = endInstant.toInstant().atZone(ZoneId.systemDefault())
                        val endHourFloat = zonedEnd.hour + zonedEnd.minute / 60f
                        (endHourFloat - startHourFloat).coerceAtLeast(0.083f) // min 5 min
                    } else {
                        0.5f
                    }

                    // Improved calculations: ensure precise alignment with hour slots
                    val itemWidth = hourSlotWidth * durationHours
                    val itemOffset = hourSlotWidth * startHourFloat

                    Box(
                        Modifier
                            .offset(x = itemOffset)
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clip(
                                if (evt is com.example.babytracker.data.SleepEvent)
                                    RoundedCornerShape(8.dp)
                                else
                                    CircleShape
                            )
                            .background(EventType.forClass(evt::class).color.copy(alpha = 0.8f))
                            .clickable { onEdit(evt) }
                    ) {
                        val note = when (evt) {
                            is com.example.babytracker.data.SleepEvent -> evt.notes
                            is com.example.babytracker.data.FeedingEvent -> evt.notes
                            is com.example.babytracker.data.DiaperEvent -> evt.notes
                            is com.example.babytracker.data.GrowthEvent -> evt.notes
                            is com.example.babytracker.data.PumpingEvent -> evt.notes
                            else -> null
                        }
                        if (!note.isNullOrBlank()) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .align(Alignment.Center),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}