package com.kouloundissa.twinstracker.ui.components


import android.util.Log
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.Family
import com.kouloundissa.twinstracker.data.Gender
import com.kouloundissa.twinstracker.data.getDisplayName
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey

@Composable
fun BabyInfoBar(
    selectedBaby: Baby?,
    onSelectBaby: (Baby) -> Unit,
    onEditBaby: () -> Unit = {},
    onAddBaby: (() -> Unit)? = null,
    onExpandedChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    familyViewModel: FamilyViewModel = hiltViewModel(),
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

    val babies by babyViewModel.babies.collectAsState()
    var currentBaby: Baby? by remember { mutableStateOf(null) }
    LaunchedEffect(babies, selectedBaby) {
        currentBaby = selectedBaby ?: babies.firstOrNull()
    }

    val families by familyViewModel.families.collectAsState()
    val selectedFamily by familyViewModel.selectedFamily.collectAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ExpandablePanel(
            headerContent = { isExpandedState ->
                // Left section: Baby info
                BabyInfoHeaderContent(
                    baby = currentBaby,
                    modifier = modifier.weight(1f)
                )

                // Right section: Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End, // Align to end
                    modifier = Modifier.padding(start = 20.dp)
                ) {
                    // Edit button
                    IconButton(
                        onClick = {
                            onEditBaby.invoke()
                            isExpanded = false
                            onExpandedChanged(isExpanded)
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

                    IconButton(
                        onClick = {
                            isExpanded = !isExpanded
                            onExpandedChanged(isExpanded)
                        },
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
            },
            expandedContent = {
                DropdownBabySelectorPanel(
                    babies = babies,
                    selectedBaby = currentBaby,
                    selectedFamily = selectedFamily,
                    families = families,
                    onSelectFamily = { family ->
                        familyViewModel.selectFamily(family)
                    },
                    onSelectBaby = { baby ->
                        onSelectBaby(baby)
                        isExpanded = false
                        onExpandedChanged(isExpanded)
                    },
                    onAddBaby = {
                        onAddBaby?.invoke()
                        isExpanded = false
                        onExpandedChanged(isExpanded)
                    },
                    onDismiss = { isExpanded = false }
                )
            },
            isExpanded = isExpanded,
            onExpandToggle = {
                if (babies.size > 1) {
                    isExpanded = !isExpanded
                    onExpandedChanged(isExpanded)
                }
            },
            onLongClick = {
                selectedBaby?.let { eventViewModel.clearBabyCache(it.id) }
            },
            modifier = modifier,
            isLoading = isLoading
        )
    }
}

@Composable
private fun BabyInfoHeaderContent(
    baby: Baby?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backgroundColor = BackgroundColor
    val tint = DarkBlue

    val babyPhotoUri = remember(baby?.photoUrl) { baby?.photoUrl?.toUri() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // Gender-based baby emoji
        if (babyPhotoUri != null) {
            val request = remember(babyPhotoUri) {
                ImageRequest.Builder(context)
                    .data(babyPhotoUri)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .listener(
                        onStart = { _request ->
                            Log.d("CoilCache", "🔄 START - Key: ${_request.data}")
                        },
                        onSuccess = { _request, result ->
                            Log.d("CoilCache", "DataSource: ${result.dataSource}")
                        }
                    )
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
            )
        } else {
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
        }

        Column(
            //  modifier = Modifier.weight(1f)
        ) {
            Text(
                text = baby?.name ?: "No baby",
                style = MaterialTheme.typography.titleLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (baby?.gender != Gender.UNKNOWN) {
                Text(
                    text = baby?.gender?.getDisplayName(context) ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tint.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FamilyNameHeader(
    familyName: String,
    hasMultipleFamilies: Boolean,
    onChangeFamilyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = DarkBlue

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FamilyRestroom, // ou Icons.Default.Home
            contentDescription = "Family",
            tint = tint.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )

        Text(
            text = familyName.ifEmpty { "No Family" },
            style = MaterialTheme.typography.bodyMedium,
            color = tint.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 6.dp)
                .weight(1f)
        )

        // Bouton pour changer de famille (seulement si plusieurs familles)
        if (hasMultipleFamilies) {
            IconButton(
                onClick = onChangeFamilyClick,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz, // ou Icons.Default.ChangeCircle
                    contentDescription = "Change Family",
                    tint = tint.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
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
    selectedFamily: Family?,
    families: List<Family>,
    onSelectFamily: (Family) -> Unit,
    onSelectBaby: (Baby) -> Unit,
    onAddBaby: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    var showFamilySelector by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (selectedFamily != null) {
            FamilyNameHeader(
                familyName = selectedFamily.name,
                hasMultipleFamilies = families.size > 1,
                onChangeFamilyClick = { showFamilySelector = true },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Sélecteur de famille (affiché si demandé)
        if (showFamilySelector && families.size > 1) {
            FamilyList(
                families = families,
                selectedFamily = selectedFamily,
                onSelect = { family ->
                    onSelectFamily(family)
                    showFamilySelector = false
                }
            )

            Divider(
                color = contentColor.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        for (baby in babies) {
            BabySelectorItem(
                baby = baby,
                isSelected = baby == selectedBaby,
                onSelect = { onSelectBaby(baby) }
            )
        }

        // Add Baby Button
        if (onAddBaby != null) {
            Divider(
                color = contentColor.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
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
    val context = LocalContext.current
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.medium

    val babyPhotoUri = remember(baby.photoUrl) { baby.photoUrl?.toUri() }

    Surface(
        tonalElevation = if (isSelected) 4.dp else 0.dp,
        color = if (isSelected) tint.copy(alpha = 0.1f)
        else backgroundColor,
        shape = cornerShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(cornerShape)
            .clickable(onClick = onSelect)
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            val request = remember(babyPhotoUri) {
                ImageRequest.Builder(context)
                    .data(babyPhotoUri)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .listener(
                        onStart = { _request ->
                            Log.d("CoilCache", "🔄 START - Key: ${_request.data}")
                        },
                        onSuccess = { _request, result ->

                            Log.d("CoilCache", "DataSource: ${result.dataSource}")
                        }
                    )
                    .build()
            }
            if (babyPhotoUri != null) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(30.dp)
                        .clip(CircleShape)

                )
            } else {
                Text(
                    text = baby.gender.emoji,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = baby.name.ifEmpty { "Unnamed Baby" },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (isSelected) tint
                        else tint.copy(alpha = 0.8f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = baby.gender.getDisplayName(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = tint.copy(alpha = 0.65f),
                    maxLines = 1
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = tint
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

    val context = LocalContext.current

    Surface(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onAddBaby),
        color = backgroundColor,
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
                text = stringResource(R.string.baby_form_title_add),
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