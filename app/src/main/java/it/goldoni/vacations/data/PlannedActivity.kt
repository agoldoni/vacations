package it.goldoni.vacations.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Attività pianificata nei dintorni di una località. */
@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("placeId")],
)
data class PlannedActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val name: String,
)
