package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventType.Companion.getDisplayName
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlin.math.ceil

@Composable
fun EventTypeCard(
    type: EventType,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    width: Dp,
    height: Dp,
    isFavorite: Boolean = false,
    onFavoriteToggle: (() -> Unit)? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val context = LocalContext.current
    val borderWidth = 1.dp
    val cornerShape = MaterialTheme.shapes.extraLarge

    val haptic = LocalHapticFeedback.current
    Surface(
        shape = cornerShape,
        color = Color.Transparent,
        border = BorderStroke(borderWidth, type.color),
        modifier = Modifier
            .width(width)
            .height(height)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { callback ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        callback()
                    }
                }
            )

    ) {
        // Background image
        AsyncImage(
            model = type.drawableRes,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.95f)
                .blur(2.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            type.color.copy(alpha = 1f),
                            type.color.copy(alpha = 0.45f),
                            type.color.copy(alpha = 0.15f),
                            backgroundColor.copy(alpha = 0.45f)
                        )
                    ),
                    shape = cornerShape,
                )
        ) {
            val cardWidth = maxWidth
            val cardHeight = maxHeight

            val showOverlay = overlayContent != null && cardWidth >= 120.dp && cardHeight >= 130.dp
            val showFavorite = onFavoriteToggle != null && cardWidth >= 90.dp && cardHeight >= 90.dp
            val showAddButton = onLongClick != null && cardWidth >= 70.dp && cardHeight >= 80.dp
            val showTitleText = cardWidth >= 100.dp && cardHeight >= 70.dp

            // Icon at top-left (always visible unless card is extremely small)
            if (!showTitleText && cardWidth >= 40.dp && cardHeight >= 40.dp) {
                Icon(
                    imageVector = type.icon,
                    contentDescription = type.getDisplayName(context),
                    tint = backgroundColor,
                    modifier = Modifier
                        .zIndex(3f)
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 12.dp)
                        .size(24.dp)
                )
            }

            // Title text at top-left (shown when enough space)
            if (showTitleText) {
                Text(
                    text = type.getDisplayName(context),
                    style = MaterialTheme.typography.titleMedium,
                    color = backgroundColor,
                    maxLines = if (cardWidth >= 140.dp) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .zIndex(3f)
                        .align(Alignment.TopStart)
                        .padding(
                            start = 24.dp,
                            top = 20.dp,
                            end = if (showFavorite) 44.dp else 24.dp
                        )
                )
            }

            // Favorite star icon at top-right
            if (showFavorite) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .zIndex(4f)
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) Color(0xFFFFD700) else contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center content - overlay
            if (showOverlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(2f)
                        .padding(horizontal = 8.dp)
                ) {
                    this@Box.overlayContent()
                }
            }

            // Small "+" button at bottom-right
            if (showAddButton) {
                IconButton(
                    onClick = onLongClick,
                    modifier = Modifier
                        .zIndex(4f)
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .background(
                            color = Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add ${type.getDisplayName(LocalContext.current)}",
                        tint = contentColor,
                        modifier = Modifier
                            .size(18.dp)
                            .background(backgroundColor.copy(alpha = 0.45f), shape = cornerShape)
                    )
                }
            }
        }
    }
}

fun calculateCardDimensions(
    constraints: BoxWithConstraintsScope,
    contentPadding: PaddingValues,
    itemCount: Int,
    spacing: Dp
): CardGridDimensions {

    val horizontalPadding = 16.dp
    val minCardSize = 55.dp
    val maxCardSize = 700.dp

    val availableWidth = constraints.maxWidth
    val availableHeight = constraints.maxHeight

    val grossWidth = availableWidth - horizontalPadding * 2
    val grossHeight = availableHeight -
            contentPadding.calculateTopPadding() -
            contentPadding.calculateBottomPadding()

    val optimalColumns = when {
        itemCount <= 2 -> 1
        itemCount <= 4 -> 2
        itemCount <= 6 -> 2
        itemCount <= 9 -> 3
        else -> minOf(4, itemCount)
    }

    val rows = ceil(itemCount.toFloat() / optimalColumns).toInt()

    val usableWidth = grossWidth - spacing * (optimalColumns - 1)
    val usableHeight = grossHeight - spacing * (rows + 1)

    val cardWidth = (usableWidth / optimalColumns).coerceIn(minCardSize, maxCardSize)
    val cardHeight = (usableHeight / rows).coerceIn(minCardSize, maxCardSize)

    // Limiter par les bornes
    val totalContentHeight = (cardHeight * rows) + (spacing * (rows + 1))
    val needsScrolling = totalContentHeight > grossHeight

    return CardGridDimensions(
        columns = optimalColumns,
        rows = rows,
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        gridHeight = grossHeight,
        needsScrolling = needsScrolling,
        totalContentHeight = totalContentHeight
    )
}

/**
 * Data class étendue avec TOUTES les infos nécessaires
 */
data class CardGridDimensions(
    val columns: Int,
    val rows: Int,
    val cardWidth: Dp,
    val cardHeight: Dp,
    val gridHeight: Dp,
    val needsScrolling: Boolean = false,
    val totalContentHeight: Dp = 0.dp
)

