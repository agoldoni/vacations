package it.goldoni.vacations.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "vacations")
data class Vacation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Data di inizio come giorni dall'epoch (LocalDate.toEpochDay) */
    val startEpochDay: Long,
) {
    val startDate: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
}
