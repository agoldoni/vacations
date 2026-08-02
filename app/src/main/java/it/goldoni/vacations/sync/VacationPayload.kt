package it.goldoni.vacations.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Payload trasferito via Bluetooth: la vacanza completa, senza id Room
 * (la semantica dell'import è replace-all, l'identità è [syncId]).
 * [formatVersion] permette l'evoluzione futura del formato.
 */
@Serializable
data class VacationPayload(
    val formatVersion: Int = FORMAT_VERSION,
    val syncId: String,
    val title: String,
    val startEpochDay: Long,
    val places: List<PlacePayload> = emptyList(),
) {
    val activityCount: Int get() = places.sumOf { it.activities.size }

    fun encode(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        const val FORMAT_VERSION = 1

        // ignoreUnknownKeys: campi aggiunti da versioni future non bloccano
        // la decodifica; l'incompatibilità vera è governata da formatVersion.
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * @throws UnsupportedPayloadException se il payload arriva da una
         *   versione dell'app più recente
         * @throws kotlinx.serialization.SerializationException se il JSON è malformato
         */
        fun decode(bytes: ByteArray): VacationPayload {
            val payload = json.decodeFromString(serializer(), bytes.decodeToString())
            if (payload.formatVersion > FORMAT_VERSION) {
                throw UnsupportedPayloadException(payload.formatVersion)
            }
            return payload
        }
    }
}

@Serializable
data class PlacePayload(
    val name: String,
    val isMain: Boolean = false,
    val activities: List<String> = emptyList(),
)

class UnsupportedPayloadException(version: Int) :
    Exception("Formato payload $version non supportato (massimo: ${VacationPayload.FORMAT_VERSION})")
