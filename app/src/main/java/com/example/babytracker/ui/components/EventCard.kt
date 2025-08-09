package com.example.babytracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.babytracker.data.event.DiaperEvent
import com.example.babytracker.data.event.Event
import com.example.babytracker.data.event.FeedingEvent
import com.example.babytracker.data.event.GrowthEvent
import com.example.babytracker.data.event.PumpingEvent
import com.example.babytracker.data.event.SleepEvent
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EventCard(event: Event) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Determine event title based on the actual subclass
            val title = when (event) {
                is FeedingEvent -> "Feeding"
                is DiaperEvent -> "Diaper Change"
                else -> "Event"
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(event.timestamp),
                style = MaterialTheme.typography.bodySmall
            )

            // Show event-specific details
            when (event) {
                is FeedingEvent -> {
                    Text("Breast: ${event.breastSide}")
                    Text("Duration: ${event.durationMinutes} min")
                }
                is DiaperEvent -> {
                    Text("Type: ${event.diaperType}")
                }
                // Other event types here...
                is GrowthEvent -> TODO()
                is PumpingEvent -> TODO()
                is SleepEvent -> TODO()
            }

            // Optional notes
            event.notes?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Notes: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}