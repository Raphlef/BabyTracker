package com.example.babytracker.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import java.util.*

@Composable
fun AddBabyScreen(
    onComplete: () -> Unit,
    viewModel: BabyViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Ajouter un bébé", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nom du bébé") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // TODO: Ajouter un sélecteur de date

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.addBaby(name, birthDate.time)
                onComplete()
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Enregistrer")
        }
    }
}