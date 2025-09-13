package com.example.babytracker.presentation.baby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.data.Baby
import com.example.babytracker.presentation.viewmodel.BabyViewModel

@Composable
fun BabySelectionScreen(
    babyViewModel: BabyViewModel = hiltViewModel(),
    onAddBaby: () -> Unit,
    onContinue: (String) -> Unit
) {

    val babies by babyViewModel.babies.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val isLoading by babyViewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    val babyToDelete = selectedBaby

    // Forcer le chargement de la liste à chaque affichage de l’écran
    LaunchedEffect(babyViewModel) {
        babyViewModel.loadBabies()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Sélectionner un bébé",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (babies.isEmpty()) {
            EmptyBabiesView(onAddBaby)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(babies.size) { index ->
                    val baby = babies[index]
                    BabyItem(
                        baby = baby,
                        selected = baby == selectedBaby,
                        onClick = { babyViewModel.selectBaby(baby) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onAddBaby,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Ajouter un bébé")
                    Spacer(Modifier.width(8.dp))
                    Text("Ajouter")
                }

                Button(
                    onClick = {
                        if (selectedBaby != null) {
                            showDeleteDialog = true
                        }
                    },
                    enabled = selectedBaby != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Supprimer")
                }

                Button(
                    onClick = { selectedBaby?.id?.let { onContinue(it) } },
                    enabled = selectedBaby != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Continuer")
                }
            }
        }
    }
    // Dialog de confirmation suppression
    if (showDeleteDialog && babyToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmer la suppression") },
            text = { Text("Voulez-vous vraiment supprimer ${babyToDelete.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        babyViewModel.deleteBaby(babyToDelete.id)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Oui")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Non")
                }
            }
        )
    }
}

@Composable
fun BabyItem(
    baby: Baby,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = baby.name.ifEmpty { "Bébé sans nom" }, style = MaterialTheme.typography.titleMedium)
                // TODO: ajouter date de naissance ou autre info utile
            }
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
        Spacer(Modifier.height(8.dp))
        Text(
            "Commencez par ajouter un bébé pour suivre sa croissance et ses événements.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddBaby) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ajouter un bébé")
        }
    }
}
