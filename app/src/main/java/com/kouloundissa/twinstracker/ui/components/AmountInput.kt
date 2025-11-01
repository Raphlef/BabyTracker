package com.kouloundissa.twinstracker.ui.components


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    label: String = "Amount (ml)",
    step: Int = 5
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    fun stringToInt(str: String): Int = str.toDoubleOrNull()?.toInt() ?: 0

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
                IconButton(
                    onClick = {
                        val currentValue = stringToInt(value)
                        val newValue = maxOf(min, currentValue - step)
                        textValue = newValue.toString()
                        onValueChange(textValue)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            BorderStroke(1.dp, contentcolor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(BackgroundColor.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = tint)
                }

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
                IconButton(
                    onClick = {
                        val currentValue = stringToInt(value)
                        val newValue = minOf(max, currentValue + step)
                        textValue = newValue.toString()
                        onValueChange(textValue)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            BorderStroke(1.dp, contentcolor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(BackgroundColor.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = contentcolor)
                }
            }

            // Quick preset buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(50, 100, 150, 200).forEach { preset ->
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

