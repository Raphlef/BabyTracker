package com.kouloundissa.twinstracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutElastic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.Gender
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlin.text.ifEmpty

@Composable
fun BabyInfoBar(
    babies: List<Baby>,
    selectedBaby: Baby?,
    onSelectBaby: (Baby) -> Unit,
    onEditBaby: () -> Unit = {},
    onAddBaby: (() -> Unit)? = null
) {
    val baseColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    var isExpanded by remember { mutableStateOf(false) }
    val expandRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic),
        label = "expandRotation"
    )

    val currentBaby = selectedBaby ?: babies.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shape = cornerShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(
                    onClick = { if (babies.size > 1) isExpanded = !isExpanded },
                    enabled = babies.size > 1
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                baseColor.copy(alpha = 0.75f),
                                baseColor.copy(alpha = 0.35f)
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
                            text = currentBaby?.gender?.emoji ?: "👶",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .scaleAnimation(
                                    targetScale = 1f,
                                    animationDuration = 300
                                )
                        )

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = currentBaby?.name ?: "Unnamed Baby",
                                style = MaterialTheme.typography.titleLarge,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (currentBaby?.gender != Gender.UNKNOWN) {
                                Text(
                                    text = currentBaby?.gender?.displayName ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Right action buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        // Edit button
                        IconButton(
                            onClick = onEditBaby,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray.copy(alpha = 0.25f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Baby",
                                tint = baseColor
                            )
                        }

                        // Expand/Collapse indicator (visible when multiple babies)
                        if (babies.size > 1) {
                            IconButton(
                                onClick = { isExpanded = !isExpanded },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.DarkGray.copy(alpha = 0.25f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle baby selector",
                                    tint = baseColor,
                                    modifier = Modifier.rotate(expandRotation)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dropdown selector with smooth animation
        DropdownBabySelectorPanel(
            babies = babies,
            selectedBaby = currentBaby,
            isExpanded = isExpanded,
            onSelectBaby = { baby ->
                onSelectBaby(baby)
                isExpanded = false
            },
            onAddBaby = onAddBaby,
            onDismiss = { isExpanded = false },
            cornerShape = cornerShape ,
            baseColor = baseColor,
            contentColor = contentColor
        )
    }
}

/**
 * Reusable dropdown selector panel with animation
 */
@Composable
private fun DropdownBabySelectorPanel(
    babies: List<Baby>,
    selectedBaby: Baby?,
    isExpanded: Boolean,
    onSelectBaby: (Baby) -> Unit,
    onAddBaby: (() -> Unit)?,
    onDismiss: () -> Unit,
    cornerShape: CornerBasedShape,
    baseColor: Color,
    contentColor: Color
) {
    val expandedHeight by animateDpAsState(
        targetValue = if (isExpanded) {
            val itemCount = babies.size + (if (onAddBaby != null) 1 else 0)
            val maxVisible = 4
            val visibleCount = minOf(itemCount, maxVisible)
            (60.dp * visibleCount) + 16.dp
        } else {
            0.dp
        },
        animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic),
        label = "panelHeight"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = EaseInOutCubic),
        label = "panelAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(expandedHeight)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (expandedHeight > 0.dp) {
            Surface(
                color = baseColor,
                tonalElevation = 8.dp,
                shape = cornerShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .alpha(alpha)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(babies) { baby ->
                        BabySelectorItem(
                            baby = baby,
                            isSelected = baby == selectedBaby,
                            onSelect = { onSelectBaby(baby) },
                            contentColor = contentColor
                        )
                    }

                    if (onAddBaby != null) {
                        item {
                            AddBabySelectorItem(
                                onAddBaby = onAddBaby,
                                contentColor = contentColor
                            )
                        }
                    }
                }
            }

            // Scrim for dismissal
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                        .zIndex(-1f)
                )
            }
        }
    }
}

/**
 * Individual baby selector item with hover effect
 */
@Composable
private fun BabySelectorItem(
    baby: Baby,
    isSelected: Boolean,
    onSelect: () -> Unit,
    contentColor: Color
) {
    var isHovered by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> contentColor.copy(alpha = 0.2f)
            isHovered -> contentColor.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "selectorItemBg"
    )

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onSelect)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = baby.gender.emoji,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = baby.name.ifEmpty { "Unnamed Baby" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = baby.gender.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.65f),
                    maxLines = 1
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = contentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 8.dp)
                        .scaleAnimation(targetScale = 1f, animationDuration = 200)
                )
            }
        }
    }
}

/**
 * Add baby button in selector panel
 */
@Composable
private fun AddBabySelectorItem(
    onAddBaby: () -> Unit,
    contentColor: Color
) {
    var isHovered by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) contentColor.copy(alpha = 0.08f)
        else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "addItemBg"
    )

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onAddBaby)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Baby",
                tint = contentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = "Add New Baby",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Reusable scale animation modifier
 */
fun Modifier.scaleAnimation(
    targetScale: Float = 1f,
    animationDuration: Int = 300
): Modifier = composed {
    var isAnimating by remember { mutableStateOf(true) }

    val scale by animateFloatAsState(
        targetValue = if (isAnimating) targetScale else targetScale,
        animationSpec = tween(durationMillis = animationDuration, easing = EaseOutElastic),
        label = "scaleAnimation",
        finishedListener = { isAnimating = false }
    )

    this.scale(scale)
}

/**
 * Extension function for easier pointer event handling
 */
fun Modifier.onPointerEvent(
    eventType: PointerEventType,
    onEvent: () -> Unit
): Modifier = this.pointerInput(eventType) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == eventType) {
                onEvent()
            }
        }
    }
}


