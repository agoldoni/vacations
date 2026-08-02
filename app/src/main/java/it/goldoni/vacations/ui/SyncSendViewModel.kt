package it.goldoni.vacations.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.goldoni.vacations.VacationsApplication
import it.goldoni.vacations.sync.BluetoothVacationTransfer
import it.goldoni.vacations.sync.TransferOutcome
import it.goldoni.vacations.sync.VacationSyncRepository
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Dispositivo associato mostrato nel picker. */
data class DeviceItem(val name: String, val device: BluetoothDevice)

sealed interface SendUiState {
    data class SelectDevice(val devices: List<DeviceItem>) : SendUiState
    data class Sending(val deviceName: String) : SendUiState
    data class Done(val outcome: TransferOutcome, val deviceName: String) : SendUiState
    data class Error(val message: String) : SendUiState
}

class SyncSendViewModel(
    private val vacationId: Long,
    private val adapter: BluetoothAdapter?,
    private val repository: VacationSyncRepository,
) : ViewModel() {

    private val transfer = adapter?.let { BluetoothVacationTransfer(it) }

    private val _state = MutableStateFlow<SendUiState>(SendUiState.SelectDevice(emptyList()))
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private var sendJob: Job? = null

    /** Da chiamare a permesso concesso: elenca i dispositivi già associati. */
    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        val bonded = adapter?.bondedDevices.orEmpty()
            .map { DeviceItem(it.name ?: it.address, it) }
            .sortedBy { it.name }
        _state.value = SendUiState.SelectDevice(bonded)
    }

    fun send(item: DeviceItem) {
        val transfer = transfer ?: return
        sendJob = viewModelScope.launch {
            _state.value = SendUiState.Sending(item.name)
            try {
                val payload = repository.export(vacationId)
                if (payload == null) {
                    _state.value = SendUiState.Error("Vacanza non trovata.")
                    return@launch
                }
                val outcome = transfer.send(item.device, payload.encode())
                _state.value = SendUiState.Done(outcome, item.name)
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                _state.value = SendUiState.Error(
                    "Connessione non riuscita. Verifica che sull'altro dispositivo " +
                        "sia aperta la schermata \"Ricevi via Bluetooth\"."
                )
            } catch (e: SecurityException) {
                _state.value = SendUiState.Error("Permesso Bluetooth mancante.")
            }
        }
    }

    /** Annulla l'invio in corso e torna alla scelta del dispositivo. */
    fun cancel() {
        sendJob?.cancel()
        refreshDevices()
    }

    companion object {
        fun factory(vacationId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as VacationsApplication
                val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
                SyncSendViewModel(
                    vacationId = vacationId,
                    adapter = adapter,
                    repository = VacationSyncRepository(app.database.syncDao()),
                )
            }
        }
    }
}
