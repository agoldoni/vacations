package it.goldoni.vacations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.goldoni.vacations.data.Place
import it.goldoni.vacations.data.PlaceWithActivities
import it.goldoni.vacations.data.PlannedActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VacationDetailScreen(
    vacationId: Long,
    onBack: () -> Unit,
    onSendClick: () -> Unit,
    viewModel: VacationDetailViewModel = viewModel(factory = VacationDetailViewModel.factory(vacationId)),
) {
    val context = LocalContext.current
    val vacation by viewModel.vacation.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()

    var showEditVacation by remember { mutableStateOf(false) }
    var showAddPlace by remember { mutableStateOf(false) }
    var placeForActivity by remember { mutableStateOf<Place?>(null) }
    var placeToDelete by remember { mutableStateOf<Place?>(null) }
    var placeForMap by remember { mutableStateOf<Place?>(null) }

    val main = places.firstOrNull { it.place.isMain }
    val others = places.filter { !it.place.isMain }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vacation?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = onSendClick) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Invia via Bluetooth")
                    }
                    IconButton(onClick = { showEditVacation = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifica vacanza")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddPlace = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Località") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vacation?.let { v ->
                item(key = "date") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Partenza: ${v.startDate.formatted()}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            item(key = "main-header") {
                Text(
                    "Baricentro",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (main != null) {
                item(key = "place-${main.place.id}") {
                    PlaceCard(
                        pwa = main,
                        isMain = true,
                        // Tap sul baricentro: stesso dialog delle azioni delle altre località
                        onClick = { placeForMap = main.place },
                        onAddActivity = { placeForActivity = main.place },
                        onDeleteActivity = viewModel::deleteActivity,
                        onDelete = { placeToDelete = main.place },
                    )
                }
            } else {
                item(key = "no-main") {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Nessuna località scelta.",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Aggiungi la prima località: diventerà il baricentro delle attività limitrofe.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (others.isNotEmpty()) {
                item(key = "others-header") {
                    Text(
                        "Altre località da visitare",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(others, key = { "place-${it.place.id}" }) { pwa ->
                    PlaceCard(
                        pwa = pwa,
                        isMain = false,
                        // Tap su altra località: chiede se aprire la mappa o il percorso
                        onClick = { placeForMap = pwa.place },
                        onAddActivity = { placeForActivity = pwa.place },
                        onDeleteActivity = viewModel::deleteActivity,
                        onDelete = { placeToDelete = pwa.place },
                    )
                }
            }
        }
    }

    if (showEditVacation) {
        vacation?.let { v ->
            VacationDialog(
                dialogTitle = "Modifica vacanza",
                confirmLabel = "Salva",
                initialTitle = v.title,
                initialDate = v.startDate,
                onDismiss = { showEditVacation = false },
                onConfirm = { title, date ->
                    viewModel.updateVacation(title, date)
                    showEditVacation = false
                },
            )
        }
    }

    if (showAddPlace) {
        TextInputDialog(
            dialogTitle = if (main == null) "Località baricentro" else "Nuova località",
            label = "Nome località",
            onDismiss = { showAddPlace = false },
            onConfirm = { name ->
                viewModel.addPlace(name)
                showAddPlace = false
            },
        )
    }

    placeForActivity?.let { place ->
        TextInputDialog(
            dialogTitle = "Attività vicino a ${place.name}",
            label = "Attività",
            onDismiss = { placeForActivity = null },
            onConfirm = { name ->
                viewModel.addActivity(place, name)
                placeForActivity = null
            },
        )
    }

    placeForMap?.let { place ->
        AlertDialog(
            onDismissRequest = { placeForMap = null },
            title = { Text(place.name) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            openPlaceInMaps(context, place.name)
                            placeForMap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Apri sulla mappa", modifier = Modifier.weight(1f))
                    }
                    // Percorso e promozione a baricentro non hanno senso
                    // per il baricentro stesso
                    if (!place.isMain) {
                        main?.let { m ->
                            TextButton(
                                onClick = {
                                    openRouteInMaps(context, from = m.place.name, to = place.name)
                                    placeForMap = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Percorso da ${m.place.name}", modifier = Modifier.weight(1f))
                            }
                        }
                        TextButton(
                            onClick = {
                                viewModel.setMain(place)
                                placeForMap = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Rendi baricentro", modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { placeForMap = null }) { Text("Annulla") }
            },
        )
    }

    placeToDelete?.let { place ->
        AlertDialog(
            onDismissRequest = { placeToDelete = null },
            title = { Text("Eliminare la località?") },
            text = { Text("“${place.name}” e le sue attività saranno eliminate.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(place)
                    placeToDelete = null
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { placeToDelete = null }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun PlaceCard(
    pwa: PlaceWithActivities,
    isMain: Boolean,
    onClick: () -> Unit,
    onAddActivity: () -> Unit,
    onDeleteActivity: (PlannedActivity) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = if (isMain) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        // Intestazione evidenziata e cliccabile: apre il dialog delle azioni
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                pwa.place.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Elimina località")
            }
        }

        Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 8.dp)) {
            pwa.activities.forEach { activity ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp),
                ) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        activity.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onDeleteActivity(activity) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Rimuovi attività",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            TextButton(onClick = onAddActivity) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isMain) "Attività nei dintorni" else "Attività")
            }
        }
    }
}
