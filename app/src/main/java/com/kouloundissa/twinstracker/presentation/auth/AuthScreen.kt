package com.kouloundissa.twinstracker.presentation.auth

import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthEvent
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthViewModel
import com.kouloundissa.twinstracker.ui.components.BackgroundContainer
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var emailError by remember { mutableStateOf<String?>(null) }

    val baseColor = BackgroundColor
    val contentColor = DarkBlue


    // Preconfigure GoogleSignInClient
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }


    // Launcher for Google Sign-In Intent
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { viewModel.handleGoogleSignInResult(it) }
                ?: viewModel.onError("No ID token from Google")
        } catch (e: ApiException) {
            viewModel.onError("Google sign-in error: ${e.statusCode}")
        }
    }
    // Collect one-time events
    LaunchedEffect(Unit) {
        viewModel.oneTimeEventFlow.collect { event ->
            when (event) {
                AuthEvent.StartGoogleSignIn -> {
                    googleLauncher.launch(googleSignInClient.signInIntent)
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
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onLoginSuccess()
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
                        .padding(horizontal = 32.dp)
                        .padding(top = 48.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Twins Tracker",
                            style = MaterialTheme.typography.headlineLarge,
                            color = BackgroundColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(40.dp))

                        // Email field
                        LabeledTextField(
                            value = state.email,
                            onValueChange = {
                                viewModel.onEmailChange(it)
                                emailError = when {
                                    it.isBlank() -> "Email requis"
                                    !ValidationUtils.isValidEmail(it) -> "Email invalide"
                                    else -> null
                                }
                            },
                            label = "Email",
                            isError = emailError != null,
                            errorMessage = emailError,
                            enabled = !state.isLoading,
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Email
                        )

                        Spacer(Modifier.height(16.dp))

                        // Password field
                        LabeledTextField(
                            value = state.password,
                            onValueChange = viewModel::onPasswordChange,
                            label = "Mot de passe",
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !state.isLoading,
                            imeAction = ImeAction.Send,
                            keyboardType = KeyboardType.Password
                        )

                        Spacer(Modifier.height(16.dp))

                        // Remember me
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.rememberMe,
                                onCheckedChange = viewModel::onRememberMeChanged,
                                enabled = !state.isLoading
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Se souvenir de moi", color = BackgroundColor)
                        }

                        Spacer(Modifier.height(24.dp))

                        val canSubmit =
                            emailError == null && state.email.isNotBlank() && state.password.isNotBlank()
                        PrimaryButton(
                            text = "Connexion",
                            onClick = { viewModel.login() },
                            enabled = canSubmit && !state.isLoading,
                            isLoading = state.isLoading
                        )

                        Spacer(Modifier.height(16.dp))

                        TextButton(
                            onClick = { viewModel.register() },
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Créer un compte", color = BackgroundColor)
                        }

                        Spacer(Modifier.height(12.dp))

                        SocialButton(
                            text = "Continuer avec Google",
                            icon = Icons.Filled.AccountCircle,
                            onClick = viewModel::loginWithGoogle,
                            modifier = Modifier.fillMaxWidth()
                        )

                        state.error?.let {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                it,
                                color = Color.Red,// MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                LoadingOverlay(isVisible = state.isLoading || state.isAuthenticated)
            }
        }
    }
}

// ValidationUtils.kt
object ValidationUtils {
    fun isValidEmail(email: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

// UiComponents.kt
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
    val contentColor = Color.White
    val cornerShape = MaterialTheme.shapes.extraLarge
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            label = { Text(label, color = Color.White) },
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
                color = Color.Red,// MaterialTheme.colorScheme.error,
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
fun LoadingOverlay(isVisible: Boolean) {
    if (isVisible) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun SocialButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(50.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
