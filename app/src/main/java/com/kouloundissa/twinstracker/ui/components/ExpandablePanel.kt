package com.kouloundissa.twinstracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandablePanel(
    headerContent: @Composable RowScope.(isExpanded: Boolean) -> Unit,
    expandedContent: @Composable () -> Unit,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    minHeight: Dp = 64.dp,
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    Box(modifier = modifier) {
        Column {
            // Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(cornerShape)
                    .combinedClickable(
                        onClick = onExpandToggle,
                        onLongClick = onLongClick ?: {}
                    )
                    .blur(if (isLoading) 3.dp else 0.dp),
                color = backgroundColor.copy(alpha = 0.85f),
                shape = cornerShape,
                border = BorderStroke(1.dp, contentColor.copy(alpha = 0.55f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column {
                        // Header with fixed minimum height
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minHeight)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                headerContent(isExpanded)
                            }
                        }

                        // Expandable Content - inside the same Surface background
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .blur(if (isLoading) 3.dp else 0.dp)
                            ) {
                                expandedContent()
                            }
                        }
                    }
                }
            }
        }
        
        // Loading Overlay
        AnimatedVisibility(
            visible = isLoading && isExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(Color.Transparent)
                    .padding(24.dp)
                    .wrapContentHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                        color = tint,
                        trackColor = tint.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
