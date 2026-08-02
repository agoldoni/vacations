package it.goldoni.vacations.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VacationDao {

    @Query("SELECT * FROM vacations ORDER BY startEpochDay")
    fun observeAll(): Flow<List<Vacation>>

    @Query("SELECT * FROM vacations WHERE id = :id")
    fun observe(id: Long): Flow<Vacation?>

    @Insert
    suspend fun insert(vacation: Vacation): Long

    @Update
    suspend fun update(vacation: Vacation)

    @Delete
    suspend fun delete(vacation: Vacation)
}
