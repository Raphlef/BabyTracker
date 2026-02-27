package com.kouloundissa.twinstracker.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.kouloundissa.twinstracker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedColorPicker(
    selectedColor: Long,
    onColorSelected: (Long) -> Unit,
    palette: List<Long> = listOf(
        0xFFE91E63, 0xFFFF6F00, 0xFF4CAF50, 0xFF9C27B0,
        0xFF3F51B5, 0xFF009688, 0xFF795548, 0xFF607D8B, 0xFF9E9E9E
    ),
    modifier: Modifier = Modifier
) {
    var showCustomPicker by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val mutablePalette = remember { palette.toMutableStateList() }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            modifier = Modifier.heightIn(min = 80.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(palette) { colorInt ->
                val color = Color(colorInt)
                val isSelected = selectedColor == colorInt

                Card(
                    modifier = Modifier
                        .size(44.dp)
                        .animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = color),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = if (isSelected) 8.dp else 2.dp
                    ),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onColorSelected(colorInt)
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // CUSTOM BUTTON
            item {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                        .clickable { showCustomPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        AdvancedColorPickerDialog(
            visible = showCustomPicker,
            initialColor = selectedColor,
            onDismiss = { showCustomPicker = false },
            onConfirm = { pickedColorLong ->
                onColorSelected(pickedColorLong)
                if (!mutablePalette.contains(pickedColorLong)) {
                    mutablePalette.add(pickedColorLong)
                }
                showCustomPicker = false
            }
        )
    }
}
@Composable
private fun AdvancedColorPickerDialog(
    visible: Boolean,
    initialColor: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    if (!visible) return

    // Isolated dialog state:
    // Reset tempColor each time the dialog opens (so "Cancel" truly cancels). [web:5]
    var tempColor by remember(initialColor, visible) { mutableStateOf(Color(initialColor)) }

    // Controller lives inside the dialog since it's only used there (keeps parent simpler). [web:12]
    val controller = rememberColorPickerController()

    Dialog(onDismissRequest = onDismiss) { // standard Compose dialog entry point [web:2]
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp, // M3 tonal elevation supported on Surface [web:7]
            modifier = Modifier
                .wrapContentSize()
                .draggable(
                    state = rememberDraggableState { /* drag-to-dismiss if needed */ },
                    orientation = Orientation.Vertical
                )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                HsvColorPicker(
                    controller = controller,
                    onColorChanged = { tempColor = it.color },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                ) // HsvColorPicker + controller usage [web:12]

                Spacer(modifier = Modifier.height(16.dp))

                Card(colors = CardDefaults.cardColors(containerColor = tempColor)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColorFor(tempColor)
                        )
                        Text(
                            text = String.format("#%08X", tempColor.value.toLong()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColorFor(tempColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel_button))
                    }
                    Button(
                        onClick = { onConfirm(tempColor.value.toLong()) }
                    ) {
                        Text(stringResource(R.string.saving_button))
                    }
                }
            }
        }
    }
}

