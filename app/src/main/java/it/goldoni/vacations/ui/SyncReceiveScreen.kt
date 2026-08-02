package it.goldoni.vacations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncReceiveScreen(
    onBack: () -> Unit,
    viewModel: SyncReceiveViewModel = viewModel(factory = SyncReceiveViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ricevi via Bluetooth") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        BluetoothGate(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LaunchedEffect(Unit) { viewModel.startListening() }

            when (val s = state) {
                is ReceiveUiState.Listening, is ReceiveUiState.Preview -> StatusColumn {
                    CircularProgressIndicator()
                    Text(
                        "In ascolto…\nSull'altro dispositivo apri la vacanza " +
                            "e tocca l'icona Bluetooth per inviarla.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }

                is ReceiveUiState.Imported -> StatusColumn {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "“${s.title}” importata.\nLa trovi nell'elenco delle vacanze.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onBack) { Text("Chiudi") }
                }

                is ReceiveUiState.Error -> StatusColumn {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = viewModel::retry) { Text("Riprova") }
                }
            }

            (state as? ReceiveUiState.Preview)?.let { preview ->
                AlertDialog(
                    onDismissRequest = viewModel::reject,
                    title = { Text("Vacanza ricevuta") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "“${preview.title}”",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Partenza: ${LocalDate.ofEpochDay(preview.startEpochDay).formatted()}"
                            )
                            Text(
                                "${preview.placeCount} località, ${preview.activityCount} attività"
                            )
                            if (preview.overwritesLocal) {
                                Text(
                                    "Questa vacanza è già presente: accettando " +
                                        "sovrascriverai le modifiche fatte su questo dispositivo.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::accept) { Text("Accetta") }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::reject) { Text("Rifiuta") }
                    },
                )
            }
        }
    }
}
