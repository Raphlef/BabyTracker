package com.kouloundissa.twinstracker.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.Firestore.FirebaseValidators.validateEmailWithMessage
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthEvent
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthState
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthStep
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EmailVerificationState
import com.kouloundissa.twinstracker.ui.components.BackgroundContainer
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    val backgroundColor = BackgroundColor
    val content = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    // Collect one-time events
    LaunchedEffect(Unit) {
        viewModel.oneTimeEventFlow.collect { event ->
            when (event) {
                AuthEvent.StartGoogleSignIn -> {
                    //googleLauncher.launch(googleSignInClient.signInIntent)
                }

                AuthEvent.Logout -> {
                    // No UI action needed here; NavHost handles navigation back to "auth"
                    // Optionally clear local input fields:
                    viewModel.clearError()
                }
            }
        }
    }

    // Trigger navigation on successful auth
    LaunchedEffect(state.currentStep) {
        if (state.currentStep is AuthStep.Success && state.userProfile != null) {
            onLoginSuccess()
        }
    }

    BackgroundContainer(backgroundRes = R.drawable.background) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())  // Enable scrolling
                        .padding(horizontal = 15.dp)
                        .padding(top = 48.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(15.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(id = R.string.twins_tracker_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = BackgroundColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(40.dp))

                        when (state.currentStep) {
                            // USER AT FORM
                            AuthStep.IdleForm -> {
                                LoginForm(
                                    state = state,
                                    onEmailChange = viewModel::onEmailChange,
                                    onPasswordChange = viewModel::onPasswordChange,
                                    onRememberMeChange = viewModel::onRememberMeChanged,
                                    onLoginClick = viewModel::login,
                                    onRegisterClick = viewModel::register,
                                    onGoogleSignInClick = viewModel::loginWithGoogle,
                                    onForgotPasswordClick = viewModel::navigateToForgotPassword,
                                )
                            }

                            // AUTHENTICATING WITH FIREBASE
                            AuthStep.Authenticating -> {
                                AuthenticatingPanel(
                                    message = stringResource(id = R.string.authenticating)
                                )
                            }

                            // LOADING PROFILE FROM FIRESTORE
                            AuthStep.LoadingProfile -> {
                                AuthenticatingPanel(
                                    message = stringResource(id = R.string.loading_profile)
                                )
                            }

                            // WAITING FOR EMAIL VERIFICATION (POST-REGISTER)
                            AuthStep.EmailVerification -> {
                                EmailVerificationPanel(
                                    email = state.email,
                                    verificationState = state.emailVerificationState,
                                    onResendClick = viewModel::resendVerificationEmail,
                                    onBackClick = viewModel::clearVerificationFlow,
                                )
                            }

                            AuthStep.ForgotPassword -> {
                                ForgotPasswordPanel(
                                    email = state.email,
                                    isLoading = state.currentStep == AuthStep.Authenticating,
                                    error = state.error,
                                    onEmailChange = viewModel::onEmailChange,
                                    onSendClick = {
                                        viewModel.initiatePasswordReset(state.email)
                                    },
                                    onBackClick = viewModel::retryAfterError,
                                    backgroundColor = BackgroundColor
                                )
                            }

                            // FATAL ERROR - Show error with retry
                            is AuthStep.Error -> {
                                ErrorPanel(
                                    error = (state.currentStep as AuthStep.Error).message,
                                    onRetryClick = viewModel::retryAfterError,
                                    backgroundColor = BackgroundColor
                                )
                            }

                            // READY TO NAVIGATE
                            AuthStep.Success -> {
                                SuccessPanel()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatingPanel(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = DarkBlue,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = DarkGrey,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorPanel(
    error: String,
    onRetryClick: () -> Unit,
    backgroundColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Error icon
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = stringResource(id = R.string.error_label),
            modifier = Modifier.size(64.dp),
            tint = Color.Red
        )

        // Error message
        Text(
            stringResource(id = R.string.error_occurred),
            style = MaterialTheme.typography.headlineMedium,
            color = backgroundColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Red,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Retry button
        PrimaryButton(
            text = stringResource(id = R.string.retry),
            onClick = onRetryClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SuccessPanel() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = stringResource(id = R.string.success_label),
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF22C55E)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            stringResource(id = R.string.auth_success_redirect),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF22C55E),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmailVerificationPanel(
    email: String,
    verificationState: EmailVerificationState,
    onResendClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val backgroundColor = BackgroundColor
    val content = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Text(
            stringResource(id = R.string.email_verification_title),
            style = MaterialTheme.typography.headlineMedium,
            color = backgroundColor,
            fontWeight = FontWeight.Bold
        )

        // Email display
        Text(
            stringResource(id = R.string.confirmation_email_sent),
            style = MaterialTheme.typography.bodyMedium,
            color = backgroundColor,
            textAlign = TextAlign.Center
        )

        Text(
            email,
            style = MaterialTheme.typography.labelLarge,
            color = DarkBlue,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Status message based on verification state
        VerificationStatusMessage(verificationState = verificationState)

        Spacer(modifier = Modifier.height(20.dp))

        // Resend button
        PrimaryButton(
            text = when (verificationState) {
                EmailVerificationState.Initial -> stringResource(id = R.string.resend_email_button)
                EmailVerificationState.Sending -> stringResource(id = R.string.sending_button)
                is EmailVerificationState.Sent -> stringResource(id = R.string.email_sent_button)
                is EmailVerificationState.Error -> stringResource(id = R.string.resend_email_button)
                EmailVerificationState.Verified -> stringResource(id = R.string.email_verified_button)
            },
            onClick = onResendClick,
            enabled = verificationState !is EmailVerificationState.Sending
                    && verificationState != EmailVerificationState.Verified,
            isLoading = verificationState is EmailVerificationState.Sending,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Back button
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id = R.string.back), color = backgroundColor)
        }
    }
}

@Composable
private fun VerificationStatusMessage(verificationState: EmailVerificationState) {
    when (verificationState) {
        EmailVerificationState.Initial -> {
            Text(
                stringResource(id = R.string.waiting_verification),
                style = MaterialTheme.typography.bodySmall,
                color = DarkGrey,
                textAlign = TextAlign.Center
            )
        }

        EmailVerificationState.Sending -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = DarkBlue
                )
                Text(
                    stringResource(id = R.string.sending),
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkBlue
                )
            }
        }

        is EmailVerificationState.Sent -> {
            Text(
                verificationState.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF22C55E), // Green
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }

        is EmailVerificationState.Error -> {
            Text(
                verificationState.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Red,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }

        EmailVerificationState.Verified -> {
            Text(
                stringResource(id = R.string.email_verified_success),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF22C55E),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LoginForm(
    state: AuthState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    val backgroundColor = BackgroundColor
    val content = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    var emailError by remember { mutableStateOf<String?>(null) }
    val isEnabled = state.currentStep == AuthStep.IdleForm

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ============================================================================
        // TIER 1: INPUT FIELDS - Email & Password in card
        // ============================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = backgroundColor.copy(alpha = 0.15f),
                    shape = cornerShape
                )
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Email field
                    LabeledTextField(
                        value = state.email,
                        onValueChange = { email ->
                            onEmailChange(email)
                            emailError = validateEmailWithMessage(email)
                        },
                        label = stringResource(id = R.string.email_label),
                        isError = emailError != null,
                        errorMessage = emailError,
                        enabled = isEnabled,
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Email
                    )

                    // Password field
                    LabeledTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = stringResource(id = R.string.password_label),
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = isEnabled,
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Password
                    )
                }
            }
        }
        // ============================================================================
        // TIER 2: OPTIONS ROW - Remember Me (left) + Forgot Password (right)
        // ============================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = backgroundColor.copy(alpha = 0.15f),
                    shape = cornerShape
                )
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Remember Me - Left side
            Row(
                modifier = Modifier
                    .clip(cornerShape)
                    .clickable(enabled = isEnabled) { onRememberMeChange(!state.rememberMe) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            color = if (state.rememberMe)
                                tint
                            else
                                backgroundColor.copy(alpha = 0.08f),
                            shape = cornerShape
                        )
                        .border(
                            width = 2.dp,
                            color = if (state.rememberMe)
                                tint
                            else
                                content.copy(alpha = 0.25f),
                            shape = cornerShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.rememberMe) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = backgroundColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Text(
                    stringResource(id = R.string.remember_me),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }

            // Forgot Password - Right side
            TextButton(
                onClick = onForgotPasswordClick,
                enabled = isEnabled,
                modifier = Modifier
                    .heightIn(40.dp)
                    .padding(horizontal = 4.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = tint,
                    disabledContentColor = tint.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(id = R.string.forgot_password),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ============================================================================
        // TIER 3: PRIMARY ACTION - Login button (full width, prominent)
        // ============================================================================

        val canSubmit = emailError == null &&
                state.email.isNotBlank() &&
                state.password.isNotBlank()

        PrimaryButton(
            text = stringResource(id = R.string.login_button),
            onClick = onLoginClick,
            enabled = canSubmit && isEnabled,
            modifier = Modifier.fillMaxWidth()
        )

        // ============================================================================
        // TIER 4: SECONDARY ACTION - Register button (full width, subtle)
        // ============================================================================
        SecondaryButton(
            text = stringResource(id = R.string.register_button),
            onClick = onRegisterClick,
            enabled = isEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
fun ForgotPasswordPanel(
    email: String,
    isLoading: Boolean,
    error: String?,
    onEmailChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onBackClick: () -> Unit,
    backgroundColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Text(
            stringResource(id = R.string.reset_password_title),
            style = MaterialTheme.typography.headlineMedium,
            color = backgroundColor,
            fontWeight = FontWeight.Bold
        )

        // Description
        Text(
            stringResource(id = R.string.reset_password_description),
            style = MaterialTheme.typography.bodyMedium,
            color = backgroundColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Email input
        LabeledTextField(
            value = email,
            onValueChange = onEmailChange,
            label = stringResource(id = R.string.email_label),
            enabled = !isLoading,
            imeAction = ImeAction.Send,
            keyboardType = KeyboardType.Email,
        )

        // Error message
        error?.let {
            Text(
                it,
                color = if (it.contains("envoyé")) Color(0xFF22C55E) else Color.Red,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Send button
        PrimaryButton(
            text = if (isLoading) stringResource(id = R.string.sending_link) else stringResource(id = R.string.send_reset_link),
            onClick = onSendClick,
            enabled = !isLoading && email.isNotBlank(),
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        // Back button
        TextButton(
            onClick = onBackClick,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id = R.string.back_to_login), color = backgroundColor)
        }
    }
}


@Composable
fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val backgroundColor = BackgroundColor
    val cornerShape = MaterialTheme.shapes.extraLarge
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.copy(color = backgroundColor),
            label = { Text(label, color = backgroundColor) },
            isError = isError,
            singleLine = singleLine,
            enabled = enabled,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType).copy(imeAction = imeAction),
            shape = cornerShape,
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(50.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val backgroundColor = BackgroundColor
    val grey = DarkGrey
    val tint = DarkBlue

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(50.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor.copy(alpha = 0.5f),
            contentColor = tint,
            disabledContainerColor = grey.copy(alpha = 0.1f),
            disabledContentColor = tint.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = tint.copy(alpha = 0.3f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
