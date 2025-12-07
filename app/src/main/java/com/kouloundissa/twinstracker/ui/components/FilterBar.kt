package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventType.Companion.getDisplayName
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey

@Composable
fun FilterBar(
    types: Set<EventType>,
    selected: Set<EventType>,
    onToggle: (EventType) -> Unit, modifier: Modifier = Modifier,
) {
    val contentColor = DarkGrey.copy(alpha = 0.5f)
    val backgroundColor = BackgroundColor.copy(alpha = 0.2f)
    val tint = DarkBlue
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { type ->
            val isSelected = type in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(type) },
                label = { Text(type.getDisplayName(context = LocalContext.current)) },
                leadingIcon = {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = type.getDisplayName(context = LocalContext.current),
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) type.color else DarkGrey.copy(alpha = 0.6f)
                    )
                },
                shape = CircleShape
            )
        }
    }
}
