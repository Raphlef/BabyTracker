package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.Gender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.ifEmpty

@Composable
fun BabyInfoBar(
    baby: Baby,
    onEditClick: () -> Unit = {}
) {
    val baseColor = Color.White
    val contentColor = MaterialTheme.colorScheme.primary
    val cornerShape = MaterialTheme.shapes.extraLarge

    Surface(
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shape = cornerShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            baseColor.copy(alpha = 0.45f),
                            baseColor.copy(alpha = 0.15f)
                        )
                    ),
                    shape = cornerShape
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Gender-based baby emoji
                    Text(
                        text = baby.gender.emoji,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(end = 12.dp)
                    )

                    Column {
                        Text(
                            text = baby.name.ifEmpty { "Unnamed Baby" },
                            style = MaterialTheme.typography.titleLarge,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (baby.gender != Gender.UNKNOWN) {
                            Text(
                                text = baby.gender.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Baby",
                        tint = contentColor
                    )
                }
            }
        }
    }
}


