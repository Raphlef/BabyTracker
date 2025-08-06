package com.example.babytracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.babytracker.data.event.Event
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
            Text(
                text = event.type.name,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(event.timestamp),
                style = MaterialTheme.typography.bodySmall
            )

            // TODO: Afficher les détails spécifiques à chaque type d'événement
            when (event) {
                is FeedingEvent -> {
                    Text("Sein: ${event.breast}")
                    Text("Durée: ${event.duration} min")
                    event.quantity?.let { Text("Quantité: $it ml") }
                }
                is DiaperEvent -> {
                    Text("Type: ${event.content.name}")
                }
                // ... autres types d'événements
            }
        }
    }
}