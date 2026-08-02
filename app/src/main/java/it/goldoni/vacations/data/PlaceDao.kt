package it.goldoni.vacations.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Transaction
    @Query("SELECT * FROM places WHERE vacationId = :vacationId ORDER BY isMain DESC, name")
    fun observeForVacation(vacationId: Long): Flow<List<PlaceWithActivities>>

    @Query("SELECT COUNT(*) FROM places WHERE vacationId = :vacationId AND isMain = 1")
    suspend fun countMain(vacationId: Long): Int

    @Insert
    suspend fun insert(place: Place): Long

    @Delete
    suspend fun delete(place: Place)

    @Query("UPDATE places SET isMain = 0 WHERE vacationId = :vacationId")
    suspend fun clearMain(vacationId: Long)

    @Query("UPDATE places SET isMain = 1 WHERE id = :placeId")
    suspend fun setMainFlag(placeId: Long)

    /** Rende [placeId] il baricentro della vacanza, togliendo il flag alle altre località. */
    @Transaction
    suspend fun setMain(vacationId: Long, placeId: Long) {
        clearMain(vacationId)
        setMainFlag(placeId)
    }

    /**
     * Inserisce una località; la prima della vacanza diventa
     * automaticamente il baricentro.
     */
    @Transaction
    suspend fun insertAutoMain(vacationId: Long, name: String) {
        val isFirstMain = countMain(vacationId) == 0
        insert(Place(vacationId = vacationId, name = name, isMain = isFirstMain))
    }
}
