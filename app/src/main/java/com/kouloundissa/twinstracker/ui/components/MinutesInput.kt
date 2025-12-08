package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey

@Composable
fun MinutesInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = stringResource(id = R.string.duration_label),
    modifier: Modifier = Modifier,
    presets: List<Int> = listOf(5, 10, 15, 20)
) {
    val haptic = LocalHapticFeedback.current

    val backgroundcolor = BackgroundColor.copy(alpha = 0.5f)
    val contentcolor = DarkGrey
    val tint = DarkBlue

    var textValue by remember(value) { mutableStateOf(value) }

    // Update local state when parent value changes
    LaunchedEffect(value) {
        textValue = value
    }

    fun stringToInt(str: String): Int = str.toDoubleOrNull()?.toInt() ?: 0
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = backgroundcolor,
        modifier = modifier,
    ) {
        Column(
            modifier = modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentcolor
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { preset ->
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                textValue = preset.toString()
                                onValueChange(preset.toString())
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .border(
                                    BorderStroke(1.dp, contentcolor.copy(alpha = 0.5f)),
                                    shape = CircleShape
                                )
                                .clip(CircleShape),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (stringToInt(value) == preset)
                                    BackgroundColor
                                else
                                    BackgroundColor.copy(alpha = 0.3f)
                            ),
                            contentPadding = PaddingValues(0.dp)
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

            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    textValue = newValue
                    newValue.toIntOrNull()?.let { onValueChange(it.toString()) }
                },
                shape = RoundedCornerShape(16.dp),
                label = { Text("Minutes", color = contentcolor) },
                textStyle = LocalTextStyle.current.copy(color = contentcolor),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    }
}

