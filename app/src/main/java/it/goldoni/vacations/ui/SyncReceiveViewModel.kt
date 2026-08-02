package it.goldoni.vacations.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.goldoni.vacations.VacationsApplication
import it.goldoni.vacations.sync.BluetoothVacationTransfer
import it.goldoni.vacations.sync.TransferOutcome
import it.goldoni.vacations.sync.UnsupportedPayloadException
import it.goldoni.vacations.sync.VacationPayload
import it.goldoni.vacations.sync.VacationSyncRepository
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReceiveUiState {
    data object Listening : ReceiveUiState

    /** Anteprima della vacanza ricevuta, in attesa di Accetta/Rifiuta. */
    data class Preview(
        val title: String,
        val startEpochDay: Long,
        val placeCount: Int,
        val activityCount: Int,
        /** True se una vacanza con lo stesso syncId esiste già: l'import la sovrascrive. */
        val overwritesLocal: Boolean,
    ) : ReceiveUiState

    data class Imported(val title: String) : ReceiveUiState
    data class Error(val message: String) : ReceiveUiState
}

class SyncReceiveViewModel(
    adapter: BluetoothAdapter?,
    private val repository: VacationSyncRepository,
) : ViewModel() {

    private val transfer = adapter?.let { BluetoothVacationTransfer(it) }

    private val _state = MutableStateFlow<ReceiveUiState>(ReceiveUiState.Listening)
    val state: StateFlow<ReceiveUiState> = _state.asStateFlow()

    private var listenJob: Job? = null
    private var decision: CompletableDeferred<Boolean>? = null

    /** Da chiamare a permesso concesso: apre l'ascolto RFCOMM. */
    fun startListening() {
        if (listenJob?.isActive == true) return
        val transfer = transfer ?: run {
            _state.value = ReceiveUiState.Error("Bluetooth non disponibile.")
            return
        }
        listenJob = viewModelScope.launch {
            _state.value = ReceiveUiState.Listening
            try {
                while (true) {
                    transfer.receive { bytes -> handlePayload(bytes) }
                    when (_state.value) {
                        // Import riuscito o errore già a schermo: l'ascolto termina
                        is ReceiveUiState.Imported, is ReceiveUiState.Error -> break
                        // Vacanza rifiutata: si torna in ascolto
                        else -> _state.value = ReceiveUiState.Listening
                    }
                }
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                _state.value = ReceiveUiState.Error("Connessione Bluetooth interrotta.")
            } catch (e: SecurityException) {
                _state.value = ReceiveUiState.Error("Permesso Bluetooth mancante.")
            }
        }
    }

    private suspend fun handlePayload(bytes: ByteArray): TransferOutcome {
        val payload = try {
            VacationPayload.decode(bytes)
        } catch (e: UnsupportedPayloadException) {
            _state.value = ReceiveUiState.Error(
                "La vacanza arriva da una versione più recente dell'app: aggiorna questa app e riprova."
            )
            return TransferOutcome.ERROR
        } catch (e: Exception) {
            _state.value = ReceiveUiState.Error("I dati ricevuti non sono validi.")
            return TransferOutcome.ERROR
        }

        val accepted = CompletableDeferred<Boolean>()
        decision = accepted
        _state.value = ReceiveUiState.Preview(
            title = payload.title,
            startEpochDay = payload.startEpochDay,
            placeCount = payload.places.size,
            activityCount = payload.activityCount,
            overwritesLocal = repository.existsLocally(payload.syncId),
        )

        if (!accepted.await()) return TransferOutcome.REJECTED

        return try {
            repository.import(payload)
            _state.value = ReceiveUiState.Imported(payload.title)
            TransferOutcome.IMPORTED
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            _state.value = ReceiveUiState.Error("Import non riuscito.")
            TransferOutcome.ERROR
        }
    }

    fun accept() {
        decision?.complete(true)
    }

    fun reject() {
        decision?.complete(false)
    }

    /** Riavvia l'ascolto dopo un errore. */
    fun retry() {
        listenJob?.cancel()
        listenJob = null
        startListening()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as VacationsApplication
                val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
                SyncReceiveViewModel(
                    adapter = adapter,
                    repository = VacationSyncRepository(app.database.syncDao()),
                )
            }
        }
    }
}
