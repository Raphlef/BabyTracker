package com.example.babytracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.babytracker.data.Baby
import com.example.babytracker.data.Gender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.ifEmpty

@Composable
fun BabyInfoBar(
    baby: Baby,
    onEditClick: () -> Unit = {}
) {
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(modifier = Modifier.padding(20.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "👶 ${baby.name.ifEmpty { "Unnamed Baby" }}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Baby"
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📅 Born: ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(
                            Date(
                                baby.birthDate
                            )
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))

                if (baby.gender != Gender.UNKNOWN) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚧ Genre : ",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val (icon, label) = when (baby.gender) {
                            Gender.MALE -> "♂" to "Male"
                            Gender.FEMALE -> "♀" to "Female"
                            Gender.OTHER -> "⚧" to "Other"
                            Gender.PREFER_NOT_TO_SAY -> "❔" to "Prefer not to say"
                            Gender.UNKNOWN -> "—" to "Unknown"
                        }
                        Text(
                            text = "$icon $label",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
