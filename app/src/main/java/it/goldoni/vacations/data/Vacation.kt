package it.goldoni.vacations.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "vacations",
    indices = [Index(value = ["syncId"], unique = true)],
)
data class Vacation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Data di inizio come giorni dall'epoch (LocalDate.toEpochDay) */
    val startEpochDay: Long,
    /**
     * Identità stabile della vacanza tra dispositivi: l'id Room è locale,
     * questo campo permette alla sync Bluetooth di riconoscere una vacanza
     * già ricevuta e aggiornarla invece di duplicarla.
     */
    val syncId: String = UUID.randomUUID().toString(),
) {
    val startDate: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
}
