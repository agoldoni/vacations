package it.goldoni.vacations.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/** Dialog di creazione/modifica vacanza: titolo + data di inizio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VacationDialog(
    dialogTitle: String,
    confirmLabel: String,
    initialTitle: String = "",
    initialDate: LocalDate? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var dateEpochDay by remember { mutableStateOf(initialDate?.toEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titolo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Campo readonly: il tocco apre il DatePicker
                val dateInteraction = remember { MutableInteractionSource() }
                LaunchedEffect(dateInteraction) {
                    dateInteraction.interactions.collect {
                        if (it is PressInteraction.Release) showDatePicker = true
                    }
                }
                OutlinedTextField(
                    value = dateEpochDay?.let { LocalDate.ofEpochDay(it).formatted() } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data di inizio") },
                    placeholder = { Text("Seleziona…") },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    interactionSource = dateInteraction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && dateEpochDay != null,
                onClick = { onConfirm(title.trim(), LocalDate.ofEpochDay(dateEpochDay!!)) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (dateEpochDay ?: LocalDate.now().toEpochDay()) * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateEpochDay = it / MILLIS_PER_DAY }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annulla") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Dialog generico con un solo campo di testo (località, attività). */
@Composable
fun TextInputDialog(
    dialogTitle: String,
    label: String,
    confirmLabel: String = "Aggiungi",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}
