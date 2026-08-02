package it.goldoni.vacations.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/** Località con le sue attività nel formato usato dall'import sync (senza id Room). */
data class SyncPlaceData(
    val name: String,
    val isMain: Boolean,
    val activities: List<String>,
)

/** Query di supporto alla sincronizzazione Bluetooth. */
@Dao
interface SyncDao {

    @Query("SELECT * FROM vacations WHERE id = :id")
    suspend fun getVacation(id: Long): Vacation?

    @Query("SELECT * FROM vacations WHERE syncId = :syncId")
    suspend fun getVacationBySyncId(syncId: String): Vacation?

    @Transaction
    @Query("SELECT * FROM places WHERE vacationId = :vacationId ORDER BY isMain DESC, name")
    suspend fun getPlacesWithActivities(vacationId: Long): List<PlaceWithActivities>

    @Insert
    suspend fun insertVacation(vacation: Vacation): Long

    @Update
    suspend fun updateVacation(vacation: Vacation)

    @Insert
    suspend fun insertPlace(place: Place): Long

    @Insert
    suspend fun insertActivity(activity: PlannedActivity)

    @Query("DELETE FROM places WHERE vacationId = :vacationId")
    suspend fun deletePlacesForVacation(vacationId: Long)

    /**
     * Import idempotente di una vacanza ricevuta: se una vacanza con lo stesso
     * [syncId] esiste già viene aggiornata in place (l'id Room locale resta
     * invariato, così le schermate aperte non si rompono) e il suo contenuto
     * sostituito integralmente; altrimenti viene creata. Le attività delle
     * località rimosse spariscono per delete-cascade.
     *
     * @return l'id Room locale della vacanza importata
     */
    @Transaction
    suspend fun upsertFromSync(
        syncId: String,
        title: String,
        startEpochDay: Long,
        places: List<SyncPlaceData>,
    ): Long {
        val existing = getVacationBySyncId(syncId)
        val vacationId = if (existing == null) {
            insertVacation(Vacation(title = title, startEpochDay = startEpochDay, syncId = syncId))
        } else {
            updateVacation(existing.copy(title = title, startEpochDay = startEpochDay))
            deletePlacesForVacation(existing.id)
            existing.id
        }
        places.forEach { place ->
            val placeId = insertPlace(
                Place(vacationId = vacationId, name = place.name, isMain = place.isMain)
            )
            place.activities.forEach { activity ->
                insertActivity(PlannedActivity(placeId = placeId, name = activity))
            }
        }
        return vacationId
    }
}
