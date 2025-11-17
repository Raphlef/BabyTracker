package com.kouloundissa.twinstracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutElastic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.Gender
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey

@Composable
fun BabyInfoBar(
    babies: List<Baby>,
    selectedBaby: Baby?,
    onSelectBaby: (Baby) -> Unit,
    onEditBaby: () -> Unit = {},
    onAddBaby: (() -> Unit)? = null,
    babyViewModel: BabyViewModel = hiltViewModel(),
    eventViewModel: EventViewModel = hiltViewModel(),
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val babyIsLoading by babyViewModel.isLoading.collectAsState()
    val eventIsLoading by eventViewModel.isLoading.collectAsState()
    val isLoading = babyIsLoading || eventIsLoading

    var isExpanded by remember { mutableStateOf(false) }

    val currentBaby = selectedBaby ?: babies.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ExpandablePanel(
            headerContent = { isExpandedState ->
                // Left section: Baby info
                BabyInfoHeaderContent(
                    baby = currentBaby,
                    modifier = Modifier.weight(1f)
                )

                // Right section: Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    // Edit button
                    IconButton(
                        onClick = {
                            onEditBaby.invoke()
                            isExpanded = false
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Baby",
                            tint = tint
                        )
                    }

                    // Expand/Collapse indicator (visible when multiple babies)
                    if (babies.size > 1) {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = "Toggle baby selector",
                                tint = tint,
                            )
                        }
                    }
                }
            },
            expandedContent = {
                // Baby selector dropdown
                DropdownBabySelectorPanel(
                    babies = babies,
                    selectedBaby = currentBaby,
                    onSelectBaby = { baby ->
                        onSelectBaby(baby)
                        isExpanded = false
                    },
                    onAddBaby = {
                        onAddBaby?.invoke()
                        isExpanded = false
                    },
                    onDismiss = { isExpanded = false }
                )
            },
            isExpanded = isExpanded,
            onExpandToggle = { if (babies.size > 1) isExpanded = !isExpanded },
            onLongClick = {
                selectedBaby?.let { eventViewModel.clearBabyCache(it.id) }
            },
            modifier = Modifier,
            isLoading = isLoading
        )
    }
}

@Composable
private fun BabyInfoHeaderContent(
    baby: Baby?,
    modifier: Modifier = Modifier
) {
    val backgroundColor = BackgroundColor
    val tint = DarkBlue
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // Gender-based baby emoji
        Text(
            text = baby?.gender?.emoji ?: "👶",
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
                text = baby?.name ?: "Unnamed Baby",
                style = MaterialTheme.typography.titleLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (baby?.gender != Gender.UNKNOWN) {
                Text(
                    text = baby?.gender?.displayName ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tint.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Reusable dropdown selector panel with animation
 */
@Composable
private fun DropdownBabySelectorPanel(
    babies: List<Baby>,
    selectedBaby: Baby?,
    onSelectBaby: (Baby) -> Unit,
    onAddBaby: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        for (baby in babies) {
            BabySelectorItem(
                baby = baby,
                isSelected = baby == selectedBaby,
                onSelect = { onSelectBaby(baby) }
            )

            if (baby != babies.last() || onAddBaby != null) {
                Divider(color = contentColor.copy(alpha = 0.1f), thickness = 1.dp)
            }
        }

        // Add Baby Button
        if (onAddBaby != null) {
            AddBabySelectorItem(
                onAddBaby = onAddBaby
            )
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
    onSelect: () -> Unit
) {

    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge
    var isHovered by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> contentColor.copy(alpha = 0.2f)
            isHovered -> BackgroundColor
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "selectorItemBg"
    )

    Surface(
        color = backgroundColor,
        shape = cornerShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(cornerShape)
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
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = baby.gender.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint.copy(alpha = 0.65f),
                    maxLines = 1
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = tint,
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
    onAddBaby: () -> Unit
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge
    var isHovered by remember { mutableStateOf(false) }



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
                tint = tint.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = "Add New Baby",
                style = MaterialTheme.typography.bodyMedium,
                color = tint.copy(alpha = 0.85f),
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



