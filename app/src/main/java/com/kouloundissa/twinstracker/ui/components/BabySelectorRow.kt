package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import kotlin.text.ifEmpty

@Composable
fun BabySelectorRow(
    babies: List<Baby>,
    selectedBaby: Baby?,
    onSelectBaby: (Baby) -> Unit,
    onAddBaby: (() -> Unit)? = null
) {
    val baseColor = BackgroundColor
    val contentColor = DarkBlue

    val cornerShape = MaterialTheme.shapes.extraLarge

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(babies) { baby ->
            val isSelected = baby == selectedBaby

            FilterChip(
                selected = isSelected,
                onClick = { onSelectBaby(baby) },
                label = {
                    Text(
                        text = baby.name.ifEmpty { "Sans nom" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.height(48.dp),
                shape = cornerShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = baseColor.copy(alpha = 0.15f),
                    labelColor = contentColor.copy(alpha = 0.85f),
                    selectedContainerColor = baseColor.copy(alpha = 0.85f),
                    selectedLabelColor = contentColor
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = contentColor.copy(alpha = 0.55f),
                    selectedBorderColor = contentColor.copy(alpha = 0.55f),
                    borderWidth = 0.5.dp,
                    selectedBorderWidth = 1.dp
                ),
                elevation = FilterChipDefaults.filterChipElevation(
                    elevation = 0.dp,
                    pressedElevation = 4.dp,
                    focusedElevation = 2.dp,
                    hoveredElevation = 2.dp,
                    draggedElevation = 0.dp,
                    disabledElevation = 0.dp
                )
            )
        }
        if (onAddBaby != null) {
            item {
                // Add button with matching design
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            onClick = onAddBaby,
                            indication = ripple(bounded = false),
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    shape = CircleShape,
                    color = baseColor.copy(alpha = 0.12f),
                    border = BorderStroke(
                        1.dp,
                        contentColor.copy(alpha = 0.55f)
                    ),
                    tonalElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Baby",
                            tint = contentColor.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}