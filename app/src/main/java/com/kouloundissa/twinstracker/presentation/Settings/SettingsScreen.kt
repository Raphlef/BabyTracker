package com.kouloundissa.twinstracker.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthStep
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.components.FamilyManagementCard
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onDismiss: () -> Unit,
) {
    // Animated visibility state
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Wrap everything in a Dialog to make it truly full-screen
    Dialog(
        onDismissRequest = {
            isVisible = false
            kotlinx.coroutines.GlobalScope.launch {
                kotlinx.coroutines.delay(300)
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Animated content with slide and fade
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(
                animationSpec = tween(150)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(100)
            ) + fadeOut(
                animationSpec = tween(150)
            )
        ) {
            SettingsScreenContent(
                navController = navController,
                onDismiss = onDismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    navController: NavController,
    onDismiss: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel(),
    familyViewModel: FamilyViewModel = hiltViewModel()
) {
    // Collect state
    val authState by authViewModel.state.collectAsState()
    val profile = authState.userProfile
    val babies by babyViewModel.babies.collectAsState()
    val families by familyViewModel.families.collectAsState()

    val isAuthLoading by authViewModel.state.map { it.currentStep == AuthStep.Authenticating || it.currentStep == AuthStep.LoadingProfile || it.currentStep == AuthStep.EmailVerification }
        .collectAsState(false)
    val isFamilyLoading by familyViewModel.state.map { it.isLoading }.collectAsState(false)
    // val isLoading = isAuthLoading || isBabyLoading || isFamilyLoading
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }
    // Local editable values
    var displayName by remember { mutableStateOf(profile?.displayName.orEmpty()) }
    var notificationsEnabled by remember { mutableStateOf(profile?.notificationsEnabled == true) }

    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AsyncImage(
            model = R.drawable.background,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 12.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(id = R.string.settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // — Family Management Section —
                item {
                    SectionCard(
                        stringResource(id = R.string.settings_family_management),
                        icon = Icons.Default.FamilyRestroom
                    )
                    {
                        FamilyManagementCard(families, isFamilyLoading)
                    }
                }
                // — Profile Section —
                item {
                    SectionCard(
                        stringResource(id = R.string.settings_profile_section),
                        icon = Icons.Default.Person
                    ) {
                        GlassCard(
                            loading = isAuthLoading
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ReadOnlyField(
                                    stringResource(id = R.string.email_label),
                                    profile?.email.orEmpty(),
                                    color = contentColor
                                )
                                OutlinedTextField(
                                    value = displayName,
                                    onValueChange = { newValue ->
                                        displayName = newValue
                                    },
                                    textStyle = LocalTextStyle.current.copy(color = contentColor),
                                    label = {
                                        Text(
                                            stringResource(id = R.string.settings_display_name_label),
                                            color = contentColor,
                                        )
                                    },
                                    singleLine = true,
                                    enabled = !isAuthLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = cornerShape,
                                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                )

                                // Save button for profile edits
                                Button(
                                    onClick = {
                                        authViewModel.updateUserProfile(
                                            mapOf("displayName" to displayName)
                                        )
                                    },
                                    enabled = !isAuthLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(id = R.string.settings_save_name_button))
                                }

                                // Logout button with confirmation
                                OutlinedButton(
                                    onClick = {
                                        showLogoutDialog = true
                                    },
                                    enabled = !isAuthLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.Red
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        Color.Red
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(id = R.string.settings_logout_button))
                                }
                            }
                        }
                    }

                    // Logout confirmation dialog
                    if (showLogoutDialog) {
                        AlertDialog(
                            onDismissRequest = { showLogoutDialog = false },
                            title = {
                                Text(stringResource(id = R.string.settings_logout_confirmation_title))
                            },
                            text = {
                                Text(stringResource(id = R.string.settings_logout_confirmation_message))
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        authViewModel.logout()
                                        showLogoutDialog = false
                                        navController.navigate("auth") {
                                            popUpTo(navController.graph.startDestinationId) {
                                                inclusive = true
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        stringResource(id = R.string.settings_logout_confirm),
                                        color = Color.Red// MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showLogoutDialog = false }
                                ) {
                                    Text(stringResource(id = R.string.cancel_button))
                                }
                            }
                        )
                    }
                }

                // — Notifications & Default Baby Section —
                item {
                    SectionCard(
                        stringResource(id = R.string.settings_notifications_section),
                        icon = Icons.Default.Notifications
                    )
                    {
                        GlassCard(
                            loading = isAuthLoading
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ToggleSetting(
                                    label = stringResource(id = R.string.settings_notifications_enabled),
                                    checked = notificationsEnabled,
                                    enabled = !isAuthLoading
                                ) { notificationsEnabled = it }

                                Button(
                                    onClick = {
                                        authViewModel.updateUserProfile(
                                            mapOf(
                                                "notificationsEnabled" to notificationsEnabled,
                                            )
                                        )
                                    },
                                    enabled = !isAuthLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(id = R.string.settings_save_settings_button))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
//————————————————————————————————————————————————————————————————————————————————
// Reusable Components
//————————————————————————————————————————————————————————————————————————————————

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClickHeader: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {

    val baseColor = BackgroundColor
    val tintColor = DarkBlue
    val contentColor = DarkGrey
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = baseColor.copy(alpha = 0.7f)
        ),
    ) {
        Column(modifier = Modifier) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onClickHeader != null) {
                        onClickHeader?.invoke()
                    }
                    .background(color = baseColor.copy(alpha = 0.9f))
                    .padding(8.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Divider between header and content
            HorizontalDivider(
                Modifier,
                DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outline
            )

            // Subcard content area
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {

    val backgroundColor = BackgroundColor
    val tint = DarkBlue

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )

        if (loading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(backgroundColor.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = tint,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadOnlyField(label: String, value: String, color: Color) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    Text(value, style = MaterialTheme.typography.bodyLarge, color = color.copy(alpha = 0.8f))
}


@Composable
private fun ToggleSetting(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val contentColor = DarkGrey
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = onToggle, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(label, color = contentColor)
    }
}

