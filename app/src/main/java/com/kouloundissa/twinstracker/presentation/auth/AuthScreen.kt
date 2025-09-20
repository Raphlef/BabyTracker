package com.kouloundissa.twinstracker.presentation.auth

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    onLoginSuccess: (firstBabyId: String?) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var emailError by remember { mutableStateOf<String?>(null) }

    // Trigger navigation on successful auth
    LaunchedEffect(state.isAuthenticated, state.firstBabyId) {
        if (state.isAuthenticated) onLoginSuccess(state.firstBabyId)
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Baby Tracker", style = MaterialTheme.typography.headlineLarge)
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
            imeAction = ImeAction.Next
        )

        Spacer(Modifier.height(16.dp))

        // Password field
        LabeledTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Mot de passe",
            visualTransformation = PasswordVisualTransformation(),
            enabled = !state.isLoading,
            imeAction = ImeAction.Done
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
            Text("Se souvenir de moi")
        }

        Spacer(Modifier.height(24.dp))

        val canSubmit = emailError == null && state.email.isNotBlank() && state.password.isNotBlank()
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
            Text("Créer un compte")
        }

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    LoadingOverlay(isVisible =  state.isLoading || state.isAuthenticated)
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
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = isError,
            singleLine = singleLine,
            enabled = enabled,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = imeAction),
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
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
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text)
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
