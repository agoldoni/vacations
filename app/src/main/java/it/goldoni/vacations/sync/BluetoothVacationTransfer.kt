package it.goldoni.vacations.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Esito applicativo comunicato dal ricevente al mittente (1 byte sul canale). */
enum class TransferOutcome(val wire: Int) {
    IMPORTED(0),
    REJECTED(1),
    ERROR(2),
}

/**
 * Trasporto di un payload tra due dispositivi già associati, via RFCOMM secure.
 * Protocollo: [4 byte lunghezza big-endian][payload] → 1 byte di esito.
 *
 * I permessi Bluetooth sono verificati dalla UI prima di arrivare qui.
 */
@SuppressLint("MissingPermission")
class BluetoothVacationTransfer(private val adapter: BluetoothAdapter) {

    /**
     * Invia [payload] a [device] e attende l'esito del ricevente.
     * @throws IOException se la connessione cade prima della conferma
     */
    suspend fun send(device: BluetoothDevice, payload: ByteArray): TransferOutcome =
        withContext(Dispatchers.IO) {
            val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            closingOnExit(socket::close) {
                socket.connect()
                val output = DataOutputStream(socket.outputStream)
                output.writeInt(payload.size)
                output.write(payload)
                output.flush()
                when (val outcome = socket.inputStream.read()) {
                    TransferOutcome.IMPORTED.wire -> TransferOutcome.IMPORTED
                    TransferOutcome.REJECTED.wire -> TransferOutcome.REJECTED
                    TransferOutcome.ERROR.wire -> TransferOutcome.ERROR
                    else -> throw IOException("Connessione chiusa senza conferma ($outcome)")
                }
            }
        }

    /**
     * Si mette in ascolto, accetta una connessione e consegna il payload a
     * [handlePayload], che può sospendere (es. in attesa della decisione
     * dell'utente) e restituisce l'esito da comunicare al mittente.
     */
    suspend fun receive(handlePayload: suspend (ByteArray) -> TransferOutcome): Unit =
        withContext(Dispatchers.IO) {
            val serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
            closingOnExit(serverSocket::close) {
                val socket = serverSocket.accept()
                closingOnExit(socket::close) {
                    // Una sola connessione per sessione di ascolto
                    serverSocket.close()
                    val input = DataInputStream(socket.inputStream)
                    val size = input.readInt()
                    if (size !in 1..MAX_PAYLOAD_BYTES) {
                        throw IOException("Dimensione payload non valida: $size")
                    }
                    val payload = ByteArray(size)
                    input.readFully(payload)
                    val outcome = handlePayload(payload)
                    socket.outputStream.write(outcome.wire)
                    socket.outputStream.flush()
                }
            }
        }

    /**
     * Esegue [block] garantendo la chiusura del socket in ogni caso, inclusa la
     * cancellazione della coroutine: connect/accept/read bloccanti non
     * rispondono alla cancellazione, ma close() da fuori li sblocca.
     */
    private suspend fun <T> closingOnExit(close: () -> Unit, block: suspend () -> T): T =
        coroutineScope {
            val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    // cancellazione attesa: chiudi e basta
                } finally {
                    runCatching(close)
                }
            }
            try {
                block()
            } finally {
                closer.cancel()
            }
        }

    companion object {
        private const val SERVICE_NAME = "VacationsSync"

        /** UUID SDP fisso dell'app: identico su tutte le build (debug e release). */
        private val SERVICE_UUID: UUID = UUID.fromString("8f7c2e5a-91d4-4b6e-a3c8-52d90e14b7f6")

        /** Una vacanza serializzata pesa pochi KB: oltre 1 MB è un payload corrotto. */
        private const val MAX_PAYLOAD_BYTES = 1_000_000
    }
}
