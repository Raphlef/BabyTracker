package com.example.babytracker.presentation.auth

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.presentation.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    onLoginSuccess: (firstBabyId: String?) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var emailError by remember { mutableStateOf<String?>(null) }

    // Email validation
    fun validateEmail(input: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(input).matches()

    // Dès que l'auth est réussie ET que firstBabyId est chargé, on notifie
    LaunchedEffect(state.isAuthenticated, state.firstBabyId) {
        if (state.isAuthenticated) {
            onLoginSuccess(state.firstBabyId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Baby Tracker", style = MaterialTheme.typography.headlineLarge)

        Spacer(modifier = Modifier.height(40.dp))

        // Email field with validation
        OutlinedTextField(
            value = state.email,
            onValueChange = {
                viewModel.onEmailChange(it)
                emailError = when {
                    it.isBlank() -> "Email requis"
                    !validateEmail(it) -> "Email invalide"
                    else -> null
                }
            },
            label = { Text("Email") },
            isError = emailError != null,
            singleLine = true,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
        )
        emailError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Champ mot de passe
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Mot de passe") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Case à cocher "Se souvenir de moi"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = state.rememberMe,
                onCheckedChange = viewModel::onRememberMeChanged,
                enabled = !state.isLoading
            )
            Spacer(Modifier.width(8.dp))
            Text("Se souvenir de moi")
        }

        Spacer(modifier = Modifier.height(24.dp))

        val canSubmit =
            emailError == null && state.email.isNotBlank() && state.password.isNotBlank()
        // Bouton de connexion
        Button(
            onClick = { viewModel.login() },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSubmit && !state.isLoading
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Connexion")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bouton d'inscription
        TextButton(
            onClick = { viewModel.register() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        ) {
            Text("Créer un compte")
        }

        // Affichage des erreurs
        state.error?.let { errorMsg ->
            Spacer(Modifier.height(16.dp))
            Text(
                errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (state.isLoading) {
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
}