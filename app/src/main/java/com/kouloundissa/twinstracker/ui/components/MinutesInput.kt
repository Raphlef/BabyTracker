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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey

@Composable
fun MinutesInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String = "Duration (minutes)",
    modifier: Modifier = Modifier
) {

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
                // Quick preset buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(5, 10, 15, 20).forEach { preset ->
                        Button(
                            onClick = { onValueChange(preset) },
                            modifier = Modifier
                                .size(40.dp)
                                .border(
                                    BorderStroke(1.dp, contentcolor.copy(alpha = 0.5f)),
                                    shape = CircleShape
                                )
                                .clip(CircleShape),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (value == preset)
                                    BackgroundColor
                                else
                                    BackgroundColor.copy(alpha = 0.3f)
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "$preset",
                                color = if (value == preset) tint else contentcolor,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Input field for custom values
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { newValue ->
                    newValue.toIntOrNull()?.let { onValueChange(it) }
                },
                label = { Text("Minutes", color = contentcolor) },
                textStyle = LocalTextStyle.current.copy(color = contentcolor),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

        }
    }
}
