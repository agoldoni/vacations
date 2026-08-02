package it.goldoni.vacations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.goldoni.vacations.data.Vacation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VacationListScreen(
    onVacationClick: (Long) -> Unit,
    onReceiveClick: () -> Unit,
    viewModel: VacationListViewModel = viewModel(factory = VacationListViewModel.Factory),
) {
    val vacations by viewModel.vacations.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var vacationToDelete by remember { mutableStateOf<Vacation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Le mie vacanze") },
                actions = {
                    IconButton(onClick = onReceiveClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.BluetoothSearching,
                            contentDescription = "Ricevi via Bluetooth",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nuova vacanza")
            }
        },
    ) { padding ->
        if (vacations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nessuna vacanza pianificata.\nTocca + per crearne una.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vacations, key = { it.id }) { vacation ->
                    ElevatedCard(
                        onClick = { onVacationClick(vacation.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(vacation.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        vacation.startDate.formatted(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { vacationToDelete = vacation }) {
                                Icon(Icons.Default.Delete, contentDescription = "Elimina vacanza")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        VacationDialog(
            dialogTitle = "Nuova vacanza",
            confirmLabel = "Crea",
            onDismiss = { showAddDialog = false },
            onConfirm = { title, date ->
                viewModel.addVacation(title, date)
                showAddDialog = false
            },
        )
    }

    vacationToDelete?.let { vacation ->
        AlertDialog(
            onDismissRequest = { vacationToDelete = null },
            title = { Text("Eliminare la vacanza?") },
            text = { Text("“${vacation.title}” e tutte le sue località e attività saranno eliminate.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteVacation(vacation)
                    vacationToDelete = null
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { vacationToDelete = null }) { Text("Annulla") }
            },
        )
    }
}
