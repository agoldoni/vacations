package it.goldoni.vacations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.goldoni.vacations.sync.TransferOutcome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSendScreen(
    vacationId: Long,
    onBack: () -> Unit,
    viewModel: SyncSendViewModel = viewModel(factory = SyncSendViewModel.factory(vacationId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invia via Bluetooth") },
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
            LaunchedEffect(Unit) { viewModel.refreshDevices() }

            when (val s = state) {
                is SendUiState.SelectDevice -> DevicePicker(
                    devices = s.devices,
                    onDeviceClick = viewModel::send,
                )

                is SendUiState.Sending -> StatusColumn {
                    CircularProgressIndicator()
                    Text(
                        "Invio a ${s.deviceName}…",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = viewModel::cancel) { Text("Annulla") }
                }

                is SendUiState.Done -> StatusColumn {
                    when (s.outcome) {
                        TransferOutcome.IMPORTED -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${s.deviceName} ha importato la vacanza.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }

                        TransferOutcome.REJECTED -> Text(
                            "L'altro dispositivo ha rifiutato la vacanza.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )

                        TransferOutcome.ERROR -> {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "L'altro dispositivo ha segnalato un errore durante l'import.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Button(onClick = onBack) { Text("Chiudi") }
                }

                is SendUiState.Error -> StatusColumn {
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
                    Button(onClick = viewModel::refreshDevices) { Text("Riprova") }
                }
            }
        }
    }
}

@Composable
private fun DevicePicker(
    devices: List<DeviceItem>,
    onDeviceClick: (DeviceItem) -> Unit,
) {
    if (devices.isEmpty()) {
        StatusColumn {
            Text(
                "Nessun dispositivo associato.\nAssocia l'altro telefono dalle " +
                    "impostazioni Bluetooth di sistema, poi torna qui.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Scegli il dispositivo a cui inviare la vacanza:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(devices, key = { it.device.address }) { item ->
            ElevatedCard(
                onClick = { onDeviceClick(item) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Colonna centrata per gli stati di avanzamento/esito. */
@Composable
internal fun StatusColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
