package com.example.babytracker.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.data.Baby
import com.example.babytracker.presentation.viewmodel.BabyViewModel

@Composable
fun BabySelectionScreen(
    onBabySelected: () -> Unit,
    viewModel: BabyViewModel = hiltViewModel()
) {
    val state by viewModel.babies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            if (state.isEmpty()) {
                EmptyBabiesView(onAddBaby = { /* TODO: Ouvrir l'ajout de bébé */ })
            } else {
                LazyColumn {
                    items(state.size) { index ->
                        BabyItem(
                            baby = state[index],
                            onClick = {
                                viewModel.selectBaby(state[index])
                                onBabySelected()
                            }
                        )
                    }
                }
            }
        }
    }

    // TODO: Ajouter un FloatingActionButton pour ajouter un nouveau bébé
}

@Composable
fun BabyItem(baby: Baby, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(baby.name, style = MaterialTheme.typography.headlineSmall)
            // TODO: Afficher plus de détails (âge, dernière activité)
        }
    }
}

@Composable
fun EmptyBabiesView(onAddBaby: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Aucun bébé enregistré", style = MaterialTheme.typography.titleMedium)
        Text("Commencez par ajouter un bébé", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddBaby) {
            Text("Ajouter un bébé")
        }
    }
}