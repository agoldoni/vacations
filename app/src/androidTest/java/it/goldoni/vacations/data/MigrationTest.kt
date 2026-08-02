package it.goldoni.vacations.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrazione1a2PreservaIDatiEBackfillaSyncId() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL("INSERT INTO vacations (title, startEpochDay) VALUES ('Mare', 20000)")
            db.execSQL("INSERT INTO vacations (title, startEpochDay) VALUES ('Monti', 20100)")
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT title, syncId FROM vacations ORDER BY title").use { cursor ->
            assertEquals(2, cursor.count)
            val syncIds = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val syncId = cursor.getString(1)
                assertTrue("syncId non backfillato", syncId.isNotBlank())
                syncIds += syncId
            }
            assertEquals("syncId duplicati dopo il backfill", 2, syncIds.size)
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
