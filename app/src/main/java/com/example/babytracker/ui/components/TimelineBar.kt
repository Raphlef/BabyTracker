import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
    val config = LocalConfiguration.current
    val barWidth = config.screenWidthDp.dp

    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color(0xFFE0E0E0))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        events.forEach { evt ->
            // For discrete events: use timestamp
            val baseDate = when (evt) {
                is com.example.babytracker.data.SleepEvent -> evt.beginTime ?: evt.timestamp
                else -> evt.timestamp
            }
            val instant = baseDate.toInstant().atZone(ZoneId.systemDefault())
            val startHour = instant.hour

            // For duration events: compute start and end
            val (widthFraction, offsetFraction) = if (evt is com.example.babytracker.data.SleepEvent) {
                val endDate = evt.endTime ?: evt.beginTime ?: evt.timestamp
                val start = (evt.beginTime ?: evt.timestamp).toInstant().atZone(ZoneId.systemDefault()).hour
                val end = endDate.toInstant().atZone(ZoneId.systemDefault()).hour
                val diff = (end - start).coerceAtLeast(1)
                Pair(diff / 24f, start / 24f)
            } else {
                Pair(1f / 24f, startHour / 24f)
            }

            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(widthFraction)
                    .offset(x = (barWidth * offsetFraction))
                    .clip(CircleShape)
                    .background(EventType.forClass(evt::class).color.copy(alpha = 0.8f))
                    .clickable { onEdit(evt) }
            )
        }
    }
}

