package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey

@Composable
fun <T> IconSelector(
    title: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    getIcon: (T) -> ImageVector,
    getLabel: (T) -> String,
    getColor: ((T) -> Color)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier

) {
    val titleColor = if (enabled) Color.White else Color.LightGray
    val backgroundcolor = BackgroundColor.copy(alpha = if (enabled) 0.5f else 0.2f)
    val contentcolor = DarkGrey
    val tint = DarkBlue

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp),
            color = titleColor
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(options) { option ->
                val itemColor = getColor?.invoke(option) ?: tint.copy(alpha = 0.2f)
                val isSelected = selected == option
                Surface(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) itemColor.copy(alpha = 0.2f) else backgroundcolor,
                    border = if (isSelected)
                        BorderStroke(2.dp, itemColor)
                    else
                        BorderStroke(1.dp, contentcolor.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .size(80.dp, 88.dp)
                        .then(
                            if (enabled)
                                Modifier.clickable { onSelect(option) }
                            else
                                Modifier
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = getIcon(option),
                            contentDescription = getLabel(option),
                            tint = if (isSelected) titleColor else contentcolor.copy(alpha = 0.8f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = getLabel(option),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) titleColor else contentcolor.copy(alpha = 0.8f),
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}