package it.goldoni.vacations.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Vacation::class, Place::class, PlannedActivity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vacationDao(): VacationDao
    abstract fun placeDao(): PlaceDao
    abstract fun activityDao(): ActivityDao
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * v1 → v2: aggiunge l'identità stabile [Vacation.syncId] per la sync
         * Bluetooth; le vacanze esistenti ricevono un id casuale (hex a 32
         * caratteri, formato diverso dagli UUID delle nuove righe ma il campo
         * è trattato ovunque come stringa opaca).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vacations ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE vacations SET syncId = lower(hex(randomblob(16)))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vacations_syncId ON vacations(syncId)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vacations.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
