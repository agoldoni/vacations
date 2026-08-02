package it.goldoni.vacations.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Località di una vacanza. Quella con [isMain] = true è il "baricentro":
 * la base da cui partono le attività limitrofe.
 */
@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(
            entity = Vacation::class,
            parentColumns = ["id"],
            childColumns = ["vacationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("vacationId")],
)
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vacationId: Long,
    val name: String,
    val isMain: Boolean = false,
)
