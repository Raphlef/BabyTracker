package com.kouloundissa.twinstracker.ui.components


import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey

@Composable
fun AmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 300,
    label: String = stringResource(id = R.string.amount_label),
    step: Int = 5,
    presets: List<Int> = listOf(50, 100, 150, 200)
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        textValue = value
    }
    fun stringToInt(str: String): Int = str.toDoubleOrNull()?.toInt() ?: 0
    val currentValue = stringToInt(value)
    val haptic = LocalHapticFeedback.current
    val backgroundcolor = BackgroundColor.copy(alpha = 0.5f)
    val contentcolor = DarkGrey
    val tint = DarkBlue


    Surface(
        shape = RoundedCornerShape(16.dp),
        color = backgroundcolor,
        modifier = modifier,
    ) {
        Column(
            modifier = modifier
                .padding(12.dp)
                .imePadding()
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = contentcolor
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decrement button
                AdjustmentButton(
                    icon = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    delta = -step,
                    onValueChange = { newValue ->
                        textValue = newValue.toString()
                        onValueChange(textValue)
                    },
                    currentValue = currentValue,
                    min = min,
                    max = max,
                )

                // Text input (center, editable)
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newText ->
                        textValue = newText
                        if (newText.isEmpty() || newText.toIntOrNull() != null || newText.toDoubleOrNull() != null) {
                            onValueChange(newText)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    textStyle = LocalTextStyle.current.copy(
                        color = contentcolor,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tint.copy(alpha = 0.5f),
                        unfocusedBorderColor = tint.copy(alpha = 0.2f)
                    )
                )

                // Increment button
                AdjustmentButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Increase",
                    delta = step,
                    onValueChange = { newValue ->
                        textValue = newValue.toString()
                        onValueChange(textValue)
                    },
                    currentValue = currentValue,
                    min = min,
                    max = max,
                )
            }

            // Quick preset buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    Button(
                        onClick = {
                            textValue = preset.toString()
                            onValueChange(textValue)
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .weight(1f)
                            .border(
                                BorderStroke(1.dp, contentcolor.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (stringToInt(value) == preset)
                                BackgroundColor.copy(alpha = 0.9f)
                            else
                                BackgroundColor.copy(alpha = 0.15f)
                        ),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text(
                            "$preset",
                            color = if (stringToInt(value) == preset) tint else contentcolor,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdjustmentButton(
    icon: ImageVector,
    contentDescription: String,
    delta: Int,
    onValueChange: (Int) -> Unit,
    currentValue: Int,
    min: Int,
    max: Int,
) {
    val backgroundcolor = BackgroundColor.copy(alpha = 0.5f)
    val contentcolor = DarkGrey
    val tint = DarkBlue
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isPressed by remember { mutableStateOf(false) }
    var isRepeating by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "button_scale"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                BorderStroke(1.dp, contentcolor.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isPressed) Color.White.copy(alpha = 0.8f)
                else backgroundcolor.copy(alpha = 0.25f)
            )
            .pointerInput(currentValue) {
                awaitEachGesture {
                    awaitFirstDown()
                    isPressed = true
                    waitForUpOrCancellation()
                    val newValue = when {
                        delta > 0 -> minOf(max, currentValue + delta)
                        delta < 0 -> maxOf(min, currentValue + delta)
                        else -> currentValue
                    }
                    if (newValue != currentValue) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onValueChange(newValue)
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentcolor)
    }
}