package it.goldoni.vacations.sync

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VacationPayloadTest {

    private val payload = VacationPayload(
        syncId = "0d5cf1f4-b1a2-4a41-9c93-2f6c1f4c2a10",
        title = "Eolie 2026",
        startEpochDay = 20_642,
        places = listOf(
            PlacePayload("Lipari", isMain = true, activities = listOf("Spiaggia bianca", "Museo archeologico")),
            PlacePayload("Vulcano", activities = listOf("Cratere")),
            PlacePayload("Salina"),
        ),
    )

    @Test
    fun `round trip di serializzazione`() {
        assertEquals(payload, VacationPayload.decode(payload.encode()))
    }

    @Test
    fun `conteggio attivita su tutte le localita`() {
        assertEquals(3, payload.activityCount)
    }

    @Test
    fun `versione futura del formato rifiutata`() {
        val json = """{"formatVersion":99,"syncId":"x","title":"T","startEpochDay":1}"""
        assertThrows(UnsupportedPayloadException::class.java) {
            VacationPayload.decode(json.encodeToByteArray())
        }
    }

    @Test
    fun `json malformato rifiutato`() {
        assertThrows(SerializationException::class.java) {
            VacationPayload.decode("non è json".encodeToByteArray())
        }
    }

    @Test
    fun `campo obbligatorio mancante rifiutato`() {
        val json = """{"formatVersion":1,"title":"T","startEpochDay":1}"""
        assertThrows(SerializationException::class.java) {
            VacationPayload.decode(json.encodeToByteArray())
        }
    }

    @Test
    fun `campi sconosciuti ignorati per compatibilita in avanti`() {
        val json = """{"formatVersion":1,"syncId":"x","title":"T","startEpochDay":1,"nuovoCampo":true}"""
        val decoded = VacationPayload.decode(json.encodeToByteArray())
        assertEquals("T", decoded.title)
        assertEquals(0, decoded.places.size)
    }
}
