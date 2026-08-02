package it.goldoni.vacations.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert

@Dao
interface ActivityDao {

    @Insert
    suspend fun insert(activity: PlannedActivity): Long

    @Delete
    suspend fun delete(activity: PlannedActivity)
}
