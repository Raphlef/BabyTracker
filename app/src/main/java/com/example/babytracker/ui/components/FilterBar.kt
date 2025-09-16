package com.example.babytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.babytracker.data.EventType

@Composable
fun FilterBar(
    types: Set<EventType>,
    selected: Set<EventType>,
    onToggle: (EventType) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { type ->
            val isSelected = type in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(type) },
                label = { Text(type.displayName) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(type.color)
                    )
                },
                shape = CircleShape
            )
        }
    }
}
