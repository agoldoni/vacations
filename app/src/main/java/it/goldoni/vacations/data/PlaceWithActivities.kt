package it.goldoni.vacations.data

import androidx.room.Embedded
import androidx.room.Relation

data class PlaceWithActivities(
    @Embedded val place: Place,
    @Relation(parentColumn = "id", entityColumn = "placeId")
    val activities: List<PlannedActivity>,
)
